package org.bitpunk.m3uedit.plugins.output;

import com.jcraft.jsch.*;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import javafx.scene.control.DialogPane;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.output.sftp.SftpTargetDialogController;
import org.bitpunk.m3uedit.plugins.output.sftp.model.SftpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

@Getter
@Setter
public class SftpOutput extends AbstractPlugin implements Output {
    private static final Logger log = LoggerFactory.getLogger(SftpOutput.class);
    public static final String DEFAULT_PLAYLIST_FILENAME = "playlist.m3u";
    @XStreamOmitField
    private final String name = "SFTP Output";
    @XStreamOmitField
    private final String description = "writes a playlist to a SFTP server.";
    private SftpConnection sftpConnection = null;

    static {
        initJschLogger();
    }

    private static void initJschLogger() {
        JSch.setLogger(new com.jcraft.jsch.Logger() {
            @Override
            public boolean isEnabled(int level) {
                return switch (level) {
                    case com.jcraft.jsch.Logger.DEBUG -> log.isDebugEnabled();
                    case com.jcraft.jsch.Logger.INFO -> log.isInfoEnabled();
                    case com.jcraft.jsch.Logger.WARN -> log.isWarnEnabled();
                    case com.jcraft.jsch.Logger.ERROR, com.jcraft.jsch.Logger.FATAL -> log.isErrorEnabled();
                    default -> false;
                };
            }

            @Override
            public void log(int level, String message) {
                switch (level) {
                    case com.jcraft.jsch.Logger.DEBUG -> log.debug("[JSch] {}", message);
                    case com.jcraft.jsch.Logger.INFO -> log.info("[JSch] {}", message);
                    case com.jcraft.jsch.Logger.WARN -> log.warn("[JSch] {}", message);
                    case com.jcraft.jsch.Logger.ERROR, com.jcraft.jsch.Logger.FATAL -> log.error("[JSch] {}", message);
                    default -> log.debug("[JSch] {}", message);
                }
            }
        });
    }

    @Override
    public void write(List<Entry> playlist) {
        log.debug("write()");
        if (playlist != null && !playlist.isEmpty() && sftpConnection != null) {
            ChannelSftp channelSftp = null;
            Session jschSession = null;
            String targetPath = null;
            int payloadBytes = 0;
            try {
                channelSftp = setupJsch();
                if (channelSftp != null) {
                    jschSession = channelSftp.getSession();
                    channelSftp.connect();
                    Writer writer = new Writer();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    writer.write(playlist, outputStream);
                    byte[] bytes = outputStream.toByteArray();
                    payloadBytes = bytes.length;

                    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

                    targetPath = resolveTargetPath(channelSftp, sftpConnection.getPath());
                    ensureParentDirectoriesExist(channelSftp, targetPath);
                    log.info("Writing playlist ({} entries, {} bytes) to SFTP server {}@{}:{} at target path '{}' (configured path: '{}')",
                            playlist.size(), payloadBytes, sftpConnection.getUserName(), sftpConnection.getHostName(),
                            sftpConnection.getPort(), targetPath, sftpConnection.getPath());
                    channelSftp.put(inputStream, targetPath);
                    log.info("Successfully wrote playlist to SFTP destination: {}", targetPath);
                }
            } catch (SftpException e) {
                String contextDetails = buildSftpContextDetails(channelSftp, targetPath, playlist.size(), payloadBytes);
                log.error("SFTP transfer failed with error code {} ({}): {} | Context: {}",
                        e.id, getSftpErrorName(e.id), e.getMessage(), contextDetails, e);
                throw new RuntimeException(e);
            } catch (JSchException e) {
                String contextDetails = buildJschContextDetails(channelSftp, targetPath, playlist.size(), payloadBytes);
                log.error("SSH/JSch session error during SFTP output: {} | Context: {}",
                        e.getMessage(), contextDetails, e);
                throw new RuntimeException(e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (jschSession != null && jschSession.isConnected()) {
                    jschSession.disconnect();
                }
            }
        }
    }

    protected String buildSftpContextDetails(ChannelSftp channelSftp, String targetPath, int playlistSize, int payloadBytes) {
        StringBuilder sb = new StringBuilder();
        if (sftpConnection != null) {
            sb.append("host=").append(sftpConnection.getHostName())
                    .append(", port=").append(sftpConnection.getPort())
                    .append(", user=").append(sftpConnection.getUserName())
                    .append(", configuredPath=").append(sftpConnection.getPath());
        }
        sb.append(", targetPath=").append(targetPath);
        sb.append(", playlistEntries=").append(playlistSize);
        sb.append(", payloadBytes=").append(payloadBytes);

        if (channelSftp != null && channelSftp.isConnected()) {
            try {
                sb.append(", remotePwd=").append(channelSftp.pwd());
            } catch (Exception ex) {
                sb.append(", remotePwd=<error: ").append(ex.getMessage()).append(">");
            }
            try {
                sb.append(", remoteHome=").append(channelSftp.getHome());
            } catch (Exception ex) {
                sb.append(", remoteHome=<error: ").append(ex.getMessage()).append(">");
            }
            try {
                sb.append(", serverVersion=").append(channelSftp.getServerVersion());
            } catch (Exception ex) {
                sb.append(", serverVersion=<error: ").append(ex.getMessage()).append(">");
            }
            if (targetPath != null) {
                try {
                    SftpATTRS targetAttrs = channelSftp.stat(targetPath);
                    sb.append(", targetExists=true")
                            .append(", targetIsDir=").append(targetAttrs.isDir())
                            .append(", targetPermissions=").append(targetAttrs.getPermissionsString())
                            .append(", targetSize=").append(targetAttrs.getSize());
                } catch (SftpException ex) {
                    sb.append(", targetStatError=").append(getSftpErrorName(ex.id)).append(" (").append(ex.getMessage()).append(")");
                }
                int lastSlash = targetPath.lastIndexOf('/');
                String parentDir = lastSlash > 0 ? targetPath.substring(0, lastSlash) : (lastSlash == 0 ? "/" : null);
                if (parentDir != null) {
                    try {
                        SftpATTRS parentAttrs = channelSftp.stat(parentDir);
                        sb.append(", parentDir=").append(parentDir)
                                .append(", parentExists=true")
                                .append(", parentIsDir=").append(parentAttrs.isDir())
                                .append(", parentPermissions=").append(parentAttrs.getPermissionsString());
                    } catch (SftpException ex) {
                        sb.append(", parentDir=").append(parentDir)
                                .append(", parentStatError=").append(getSftpErrorName(ex.id)).append(" (").append(ex.getMessage()).append(")");
                    }
                }
            }
        } else {
            sb.append(", channelConnected=false");
        }
        return sb.toString();
    }

    protected String buildJschContextDetails(ChannelSftp channelSftp, String targetPath, int playlistSize, int payloadBytes) {
        StringBuilder sb = new StringBuilder();
        if (sftpConnection != null) {
            sb.append("host=").append(sftpConnection.getHostName())
                    .append(", port=").append(sftpConnection.getPort())
                    .append(", user=").append(sftpConnection.getUserName())
                    .append(", configuredPath=").append(sftpConnection.getPath());
        }
        sb.append(", targetPath=").append(targetPath);
        sb.append(", playlistEntries=").append(playlistSize);
        sb.append(", payloadBytes=").append(payloadBytes);
        sb.append(", channelConnected=").append(channelSftp != null && channelSftp.isConnected());
        return sb.toString();
    }

    protected String getSftpErrorName(int id) {
        return switch (id) {
            case ChannelSftp.SSH_FX_OK -> "SSH_FX_OK (0)";
            case ChannelSftp.SSH_FX_EOF -> "SSH_FX_EOF (1)";
            case ChannelSftp.SSH_FX_NO_SUCH_FILE -> "SSH_FX_NO_SUCH_FILE (2)";
            case ChannelSftp.SSH_FX_PERMISSION_DENIED -> "SSH_FX_PERMISSION_DENIED (3)";
            case ChannelSftp.SSH_FX_FAILURE -> "SSH_FX_FAILURE (4)";
            case ChannelSftp.SSH_FX_BAD_MESSAGE -> "SSH_FX_BAD_MESSAGE (5)";
            case ChannelSftp.SSH_FX_NO_CONNECTION -> "SSH_FX_NO_CONNECTION (6)";
            case ChannelSftp.SSH_FX_CONNECTION_LOST -> "SSH_FX_CONNECTION_LOST (7)";
            case ChannelSftp.SSH_FX_OP_UNSUPPORTED -> "SSH_FX_OP_UNSUPPORTED (8)";
            default -> "SSH_FX_UNKNOWN (" + id + ")";
        };
    }

    protected void ensureParentDirectoriesExist(ChannelSftp channelSftp, String targetPath) {
        if (channelSftp == null || targetPath == null) {
            return;
        }
        int lastSlash = targetPath.lastIndexOf('/');
        if (lastSlash > 0) {
            String parentDir = targetPath.substring(0, lastSlash);
            String[] folders = parentDir.split("/");
            StringBuilder currentPath = new StringBuilder();
            if (targetPath.startsWith("/")) {
                currentPath.append("/");
            }
            for (String folder : folders) {
                if (!folder.isEmpty()) {
                    if (currentPath.length() > 1 && !currentPath.toString().endsWith("/")) {
                        currentPath.append("/");
                    }
                    currentPath.append(folder);
                    String pathToCheck = currentPath.toString();
                    try {
                        SftpATTRS attrs = channelSftp.stat(pathToCheck);
                        if (!attrs.isDir()) {
                            log.warn("Remote path element '{}' exists but is NOT a directory (permissions={}, size={})",
                                    pathToCheck, attrs.getPermissionsString(), attrs.getSize());
                        } else {
                            log.debug("Remote directory path element verified: {}", pathToCheck);
                        }
                    } catch (SftpException e) {
                        log.debug("Remote directory path element '{}' does not exist (error: {} - {}); attempting to create",
                                pathToCheck, getSftpErrorName(e.id), e.getMessage());
                        try {
                            channelSftp.mkdir(pathToCheck);
                            log.debug("Successfully created remote directory: {}", pathToCheck);
                        } catch (SftpException mkdirEx) {
                            log.warn("Failed to create remote directory '{}' (error: {} - {})",
                                    pathToCheck, getSftpErrorName(mkdirEx.id), mkdirEx.getMessage());
                        }
                    }
                }
            }
        }
    }

    protected String resolveTargetPath(ChannelSftp channelSftp, String path) {
        if (path == null || path.isEmpty()) {
            return DEFAULT_PLAYLIST_FILENAME;
        }
        if (path.endsWith("/")) {
            return path + DEFAULT_PLAYLIST_FILENAME;
        }
        try {
            if (channelSftp != null) {
                SftpATTRS attrs = channelSftp.stat(path);
                if (attrs != null && attrs.isDir()) {
                    return path + "/" + DEFAULT_PLAYLIST_FILENAME;
                }
            }
        } catch (SftpException ignored) {
            // Path does not exist or is not a directory; treat as file destination
        }
        return path;
    }

    protected ChannelSftp setupJsch() throws JSchException {
        ChannelSftp channelSftp = null;
        if (sftpConnection != null) {
            JSch jsch = new JSch();
            String knownHostsPath = System.getProperty("user.home") + "/.ssh/known_hosts";
            if (new File(knownHostsPath).exists()) {
                jsch.setKnownHosts(knownHostsPath);
            }
            Session jschSession = jsch.getSession(sftpConnection.getUserName(), sftpConnection.getHostName(), sftpConnection.getPort());
            jschSession.setPassword(sftpConnection.getPassword());
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            jschSession.setConfig(config);
            jschSession.connect();
            channelSftp = (ChannelSftp) jschSession.openChannel("sftp");
        }
        return channelSftp;
    }

    @Override
    public void configure() {
        log.debug("configure()");
        SftpTargetDialogController sftpTargetDialogController = new SftpTargetDialogController();
        DialogPane dialogPane = sftpTargetDialogController.getDialogPane();
        dialogPane.lookupButton(sftpTargetDialogController.okButtonType)
                .disableProperty()
                .bind(sftpTargetDialogController.getInputsFull().not());
        sftpTargetDialogController.showAndWait().ifPresent(uri -> sftpConnection = uri);
    }

    @Override
    public URI getOutput() {
        try {
            return sftpConnection != null ? sftpConnection.toURI() : null;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
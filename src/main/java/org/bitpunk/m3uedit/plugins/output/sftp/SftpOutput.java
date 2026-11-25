package org.bitpunk.m3uedit.plugins.output.sftp;

import com.jcraft.jsch.*;
import javafx.scene.control.DialogPane;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.PluginType;
import org.bitpunk.m3uedit.plugins.output.Export;
import org.bitpunk.m3uedit.plugins.output.Export.ExportFile;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.OutputException;
import org.bitpunk.m3uedit.plugins.sftp.RecentSftpServers;
import org.bitpunk.m3uedit.plugins.sftp.SftpClient;
import org.bitpunk.m3uedit.plugins.sftp.SftpConnectionDialogController;
import org.bitpunk.m3uedit.plugins.sftp.SftpUserInteraction;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Slf4j
@PluginType(id = "SftpOutput", nameKey = "output.sftp.name")
public class SftpOutput extends AbstractPlugin implements Output {
    public static final String DEFAULT_PLAYLIST_FILENAME = SftpConnection.DEFAULT_FILE_NAME;
    private SftpConnection sftpConnection = null;

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("output.sftp.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("output.sftp.description");
    }

    static {
        initJschLogger();
    }

    /**
     * Routes JSch logging to SLF4J. JSch INFO is very verbose (key exchange, authentication and even a
     * "Socket closed" stack trace on every regular disconnect), so it is logged as DEBUG.
     */
    private static void initJschLogger() {
        JSch.setLogger(new com.jcraft.jsch.Logger() {
            @Override
            public boolean isEnabled(int level) {
                return switch (level) {
                    case com.jcraft.jsch.Logger.WARN -> log.isWarnEnabled();
                    case com.jcraft.jsch.Logger.ERROR, com.jcraft.jsch.Logger.FATAL -> log.isErrorEnabled();
                    default -> log.isDebugEnabled();
                };
            }

            @Override
            public void log(int level, String message) {
                log(level, message, null);
            }

            @Override
            public void log(int level, String message, Throwable cause) {
                switch (level) {
                    case com.jcraft.jsch.Logger.WARN -> log.warn("[JSch] {}", message, cause);
                    case com.jcraft.jsch.Logger.ERROR, com.jcraft.jsch.Logger.FATAL -> log.error("[JSch] {}", message, cause);
                    default -> log.debug("[JSch] {}", message, cause);
                }
            }
        });
    }

    @Override
    public void write(Export export) {
        log.debug("write()");
        List<Entry> playlist = export.playlist();
        if (!playlist.isEmpty() && sftpConnection != null) {
            SftpUserInteraction userInteraction = createUserInteraction();
            if (!userInteraction.ensurePassword(sftpConnection)) {
                log.warn("Writing to SFTP server {}@{}:{} skipped: no password entered",
                        sftpConnection.getUserName(), sftpConnection.getHostName(), sftpConnection.getPort());
                return;
            }
            ChannelSftp channelSftp = null;
            String targetPath = null;
            int payloadBytes = 0;
            try (SftpClient client = SftpClient.connect(sftpConnection, userInteraction)) {
                channelSftp = client.channel();
                targetPath = sftpConnection.isLegacyPath()
                        ? resolveTargetPath(channelSftp, sftpConnection.getPath())
                        : sftpConnection.getTargetPath();
                ensureParentDirectoriesExist(channelSftp, targetPath);
                if (!sftpConnection.isOverwriteWithoutAsking()
                        && !confirmOverwriteIfExists(targetPath, describeExistingFile(channelSftp, targetPath), userInteraction)) {
                    log.info("Writing to SFTP destination {} skipped: overwriting the existing file was not confirmed", targetPath);
                    return;
                }
                // the additional files are written first, so the playlist never references a file not written
                Map<ExportFile, String> fileNames = putFiles(channelSftp, targetPath, export.files());

                Writer writer = new Writer();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                writer.write(playlist, export.headerAttributes(file -> playerUrlOf(fileNames.get(file))), outputStream);
                byte[] bytes = outputStream.toByteArray();
                payloadBytes = bytes.length;
                ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                log.info("Writing playlist ({} entries, {} bytes) to SFTP server {}@{}:{} at target path '{}' (configured path: '{}')",
                        playlist.size(), payloadBytes, sftpConnection.getUserName(), sftpConnection.getHostName(),
                        sftpConnection.getPort(), targetPath, sftpConnection.getPath());
                channelSftp.put(inputStream, targetPath);
                log.info("Successfully wrote playlist to SFTP destination: {}", targetPath);
                recordRecentServer();
            } catch (SftpException e) {
                String contextDetails = buildSftpContextDetails(channelSftp, targetPath, playlist.size(), payloadBytes);
                log.error("SFTP transfer failed with error code {} ({}): {} | Context: {}",
                        e.id, getSftpErrorName(e.id), e.getMessage(), contextDetails, e);
                throw new OutputException(describeFailure(e), e);
            } catch (JSchException e) {
                if (SftpClient.isAuthenticationFailure(e)) {
                    // ask again on the next attempt instead of retrying a wrong password
                    sftpConnection.setPassword(null);
                }
                String contextDetails = buildJschContextDetails(channelSftp, targetPath, playlist.size(), payloadBytes);
                log.error("SSH/JSch session error during SFTP output: {} | Context: {}",
                        e.getMessage(), contextDetails, e);
                throw new OutputException(describeFailure(e), e);
            }
        }
    }

    /**
     * Uploads the additional files next to the playlist, overwriting existing ones: they belong to the playlist.
     *
     * @return the file name of each file uploaded
     */
    private Map<ExportFile, String> putFiles(ChannelSftp channelSftp, String playlistPath, List<ExportFile> files)
            throws SftpException {
        Map<ExportFile, String> fileNames = new IdentityHashMap<>();
        int lastSlash = playlistPath.lastIndexOf('/');
        String directory = lastSlash >= 0 ? playlistPath.substring(0, lastSlash + 1) : "";
        String playlistFileName = playlistPath.substring(lastSlash + 1);
        for (ExportFile file : files) {
            String fileName = file.fileNameFor(playlistFileName);
            log.info("Writing {} to SFTP destination {}", file.name(), directory + fileName);
            try (InputStream inputStream = Files.newInputStream(file.content())) {
                channelSftp.put(inputStream, directory + fileName);
            } catch (IOException e) {
                throw new OutputException(Messages.get("output.sftp.fileReadError", file.name(), e.getMessage()), e);
            }
            fileNames.put(file, fileName);
        }
        return fileNames;
    }

    /**
     * Players read the additional files via the base url configured for this output (e.g. a web server serving
     * the target directory). Without one, the files are not referenced by the playlist.
     *
     * @return the url players read the file with the given name from, null if unknown
     */
    String playerUrlOf(String fileName) {
        String baseUrl = sftpConnection.getPlayerBaseUrl();
        if (fileName == null || baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.strip();
        return (base.endsWith("/") ? base : base + "/") + fileName;
    }

    /**
     * Remembers the target in the "recently used servers" list; failures there must not break the write.
     */
    protected void recordRecentServer() {
        try {
            new RecentSftpServers().recordSuccess(sftpConnection);
        } catch (RuntimeException e) {
            log.warn("Could not update list of recently used SFTP servers: {}", e.getMessage(), e);
        }
    }

    /**
     * @return size and modification date of an existing file, or null if there is no file at the path
     */
    static String describeExistingFile(ChannelSftp channelSftp, String path) {
        try {
            SftpATTRS attrs = channelSftp.stat(path);
            if (attrs.isDir()) {
                return null;
            }
            String modified = LocalDateTime.ofInstant(Instant.ofEpochSecond(attrs.getMTime()), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return Messages.get("output.sftp.existingFile", attrs.getSize(), modified);
        } catch (SftpException e) {
            return null; // does not exist (or cannot be read; then the upload reports the error)
        }
    }

    /**
     * Asks the user before an existing file is overwritten.
     *
     * @param existingFileInfo description of the existing file, null if there is none
     * @return true if the file may be written
     */
    protected boolean confirmOverwriteIfExists(String targetPath, String existingFileInfo, SftpUserInteraction userInteraction) {
        if (existingFileInfo == null || sftpConnection.isOverwriteWithoutAsking()) {
            return true;
        }
        String target = sftpConnection.getUserName() + "@" + sftpConnection.getHostName() + ":"
                + sftpConnection.getPort() + targetPath;
        return switch (userInteraction.confirmOverwrite(target, existingFileInfo)) {
            case CANCEL -> false;
            case OVERWRITE -> true;
            case OVERWRITE_ALWAYS -> {
                log.info("Overwriting {} without asking from now on", target);
                sftpConnection.setOverwriteWithoutAsking(true);
                yield true;
            }
        };
    }

    protected SftpUserInteraction createUserInteraction() {
        return SftpUserInteraction.create();
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

        String parentDir = extractParentPath(targetPath);
        if (parentDir == null || parentDir.isEmpty()) {
            return;
        }

        String[] folders = parentDir.split("/");
        StringBuilder currentPath = new StringBuilder(targetPath.startsWith("/") ? "/" : "");

        for (String folder : folders) {
            if (folder.isEmpty()) {
                continue;
            }
            if (!currentPath.isEmpty() && !currentPath.toString().endsWith("/")) {
                currentPath.append("/");
            }
            currentPath.append(folder);
            verifyOrCreateDirectory(channelSftp, currentPath.toString());
        }
    }

    private String extractParentPath(String targetPath) {
        int lastSlash = targetPath.lastIndexOf('/');
        return lastSlash > 0 ? targetPath.substring(0, lastSlash) : null;
    }

    private void verifyOrCreateDirectory(ChannelSftp channelSftp, String dirPath) {
        try {
            SftpATTRS attrs = channelSftp.stat(dirPath);
            if (!attrs.isDir()) {
                log.warn("Remote path element '{}' exists but is NOT a directory (permissions={}, size={})",
                        dirPath, attrs.getPermissionsString(), attrs.getSize());
            } else {
                log.debug("Remote directory path element verified: {}", dirPath);
            }
        } catch (SftpException e) {
            log.debug("Remote directory path element '{}' does not exist (error: {} - {}); attempting to create",
                    dirPath, getSftpErrorName(e.id), e.getMessage());
            try {
                channelSftp.mkdir(dirPath);
                log.debug("Successfully created remote directory: {}", dirPath);
            } catch (SftpException mkdirEx) {
                log.warn("Failed to create remote directory '{}' (error: {} - {})",
                        dirPath, getSftpErrorName(mkdirEx.id), mkdirEx.getMessage());
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

    @Override
    public void configure() {
        log.debug("configure()");
        SftpConnectionDialogController sftpTargetDialogController = new SftpConnectionDialogController(SftpConnectionDialogController.Mode.OUTPUT);
        sftpTargetDialogController.setSftpConnection(sftpConnection);
        DialogPane dialogPane = sftpTargetDialogController.getDialogPane();
        dialogPane.lookupButton(sftpTargetDialogController.okButtonType)
                .disableProperty()
                .bind(sftpTargetDialogController.getInputsFull().not());
        sftpTargetDialogController.showAndWait().ifPresent(uri -> sftpConnection = uri);
    }

    private String describeFailure(Exception e) {
        return Messages.get("output.sftp.uploadFailed", sftpConnection.getUserName(), sftpConnection.getHostName(),
                sftpConnection.getPort(), SftpClient.describeError(e));
    }

    @Override
    public URI getOutput() {
        try {
            return sftpConnection != null ? sftpConnection.toURI() : null;
        } catch (URISyntaxException e) {
            // shown in the output table, must not fail
            log.warn("Invalid SFTP connection data: {}", e.getMessage());
            return null;
        }
    }
}

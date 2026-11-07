package org.bitpunk.m3uedit.plugins.output.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;
import org.bitpunk.m3uedit.plugins.output.sftp.model.SftpConnection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A connected SFTP session (SSH session plus SFTP channel), shared by {@link SftpOutput} and the
 * {@link SftpBrowserDialog}.
 * <p>
 * Host keys are verified against {@code ~/.ssh/known_hosts}: unknown hosts must be confirmed via
 * {@link UserInfo#promptYesNo(String)} (and are then added to the file), changed host keys are rejected.
 */
public final class SftpClient implements AutoCloseable {
    static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private final Session session;
    private final ChannelSftp channel;

    /**
     * An element of a remote directory listing.
     */
    public record RemoteEntry(String name, boolean directory) {
    }

    private SftpClient(Session session, ChannelSftp channel) {
        this.session = session;
        this.channel = channel;
    }

    /**
     * Opens the SSH session and the SFTP channel. The password of the connection must be set.
     */
    public static SftpClient connect(SftpConnection connection, UserInfo userInfo) throws JSchException {
        JSch jsch = new JSch();
        // set even if the file does not exist yet: JSch creates it once the user accepts a host key
        jsch.setKnownHosts(getKnownHostsPath());
        Session session = jsch.getSession(connection.getUserName(), connection.getHostName(), connection.getPort());
        try {
            session.setPassword(connection.getPassword().getBytes(StandardCharsets.UTF_8));
            session.setConfig("StrictHostKeyChecking", "ask");
            session.setUserInfo(userInfo);
            session.connect(CONNECT_TIMEOUT_MILLIS);
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MILLIS);
            return new SftpClient(session, channel);
        } catch (JSchException | RuntimeException e) {
            session.disconnect();
            throw e;
        }
    }

    public ChannelSftp channel() {
        return channel;
    }

    /**
     * @return the canonical absolute form of the path as resolved by the server (handles "..", "." and "")
     */
    public String canonicalPath(String path) throws SftpException {
        return channel.realpath(path == null || path.isBlank() ? "." : path);
    }

    /**
     * Lists a directory: sub directories first, then files, each sorted by name (case-insensitive).
     * "." and ".." are not included. Symbolic links pointing to directories are listed as directories.
     */
    public List<RemoteEntry> list(String directory) throws SftpException {
        List<RemoteEntry> entries = new ArrayList<>();
        for (ChannelSftp.LsEntry lsEntry : channel.ls(directory)) {
            String name = lsEntry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            entries.add(new RemoteEntry(name, isDirectory(directory, lsEntry)));
        }
        entries.sort(Comparator.comparing(RemoteEntry::directory).reversed()
                .thenComparing(RemoteEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private boolean isDirectory(String directory, ChannelSftp.LsEntry lsEntry) {
        SftpATTRS attrs = lsEntry.getAttrs();
        if (attrs.isDir()) {
            return true;
        }
        if (attrs.isLink()) {
            try {
                return channel.stat(child(directory, lsEntry.getFilename())).isDir();
            } catch (SftpException e) {
                return false; // dangling link
            }
        }
        return false;
    }

    /**
     * @return the path of an element inside a directory
     */
    static String child(String directory, String name) {
        return directory.endsWith("/") ? directory + name : directory + "/" + name;
    }

    /**
     * @return the parent directory of an absolute path ("/" for "/" itself)
     */
    static String parent(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : trimmed.substring(0, lastSlash);
    }

    static String getKnownHostsPath() {
        return Path.of(System.getProperty("user.home"), ".ssh", "known_hosts").toString();
    }

    public boolean isConnected() {
        return channel.isConnected() && session.isConnected();
    }

    @Override
    public void close() {
        if (channel.isConnected()) {
            channel.disconnect();
        }
        if (session.isConnected()) {
            session.disconnect();
        }
    }
}

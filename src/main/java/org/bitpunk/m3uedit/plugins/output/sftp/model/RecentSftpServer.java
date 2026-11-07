package org.bitpunk.m3uedit.plugins.output.sftp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An SFTP target that was used successfully. Contains no password.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentSftpServer {
    private String hostName;
    private int port;
    private String userName;
    private String path;
    private String fileName;

    public static RecentSftpServer of(SftpConnection connection) {
        return new RecentSftpServer(connection.getHostName(), connection.getPort(), connection.getUserName(),
                connection.getPath(), connection.getFileName());
    }

    /**
     * @return a new connection with these values (without password)
     */
    public SftpConnection toConnection() {
        SftpConnection connection = new SftpConnection();
        connection.setHostName(hostName);
        connection.setPort(port);
        connection.setUserName(userName);
        connection.setPath(path);
        connection.setFileName(fileName);
        return connection;
    }

    /**
     * @return e.g. "root@192.168.178.22:22/storage/playlist.m3u"
     */
    public String displayName() {
        String target = toConnection().getTargetPath();
        return userName + "@" + hostName + ":" + port + (target == null ? "" : target);
    }

    /**
     * @return true if both entries denote the same target (same server, user and target path)
     */
    public boolean isSameTarget(RecentSftpServer other) {
        return other != null && port == other.port
                && equalsIgnoreCase(hostName, other.hostName)
                && java.util.Objects.equals(userName, other.userName)
                && java.util.Objects.equals(toConnection().getTargetPath(), other.toConnection().getTargetPath());
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}

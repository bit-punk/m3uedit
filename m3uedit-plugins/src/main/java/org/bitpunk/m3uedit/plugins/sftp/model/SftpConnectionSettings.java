package org.bitpunk.m3uedit.plugins.sftp.model;

import java.util.Objects;

/**
 * The connection of the SFTP input and output as stored in project-files: the fields of {@link SftpConnection}
 * <b>without</b> the password, which is kept in memory only.
 *
 * @param schema                 always {@code sftp}
 * @param hostName               the host of the server
 * @param port                   the port of the server
 * @param path                   the directory, in older project-files (without file name) the complete file path
 * @param fileName               the file name inside the directory, null in older project-files
 * @param userName               the user to log in with
 * @param overwriteWithoutAsking output only: if false, the user is asked before an existing file is overwritten
 * @param playerBaseUrl          output only: the url players read the target directory from, optional
 */
public record SftpConnectionSettings(String schema, String hostName, int port, String path, String fileName,
                                     String userName, boolean overwriteWithoutAsking, String playerBaseUrl) {

    /**
     * @return the settings of the connection (without its password), null if there is no connection
     */
    public static SftpConnectionSettings of(SftpConnection connection) {
        if (connection == null) {
            return null;
        }
        return new SftpConnectionSettings(connection.getSchema(), connection.getHostName(), connection.getPort(),
                connection.getPath(), connection.getFileName(), connection.getUserName(),
                connection.isOverwriteWithoutAsking(), connection.getPlayerBaseUrl());
    }

    /**
     * @param previous the connection used so far, its password is kept if the server and the user stay the same
     *                 (null if there is none)
     * @return a new connection with these settings
     */
    public SftpConnection toConnection(SftpConnection previous) {
        SftpConnection connection = new SftpConnection();
        connection.setSchema(schema == null ? SftpConnection.SFTP_SCHEMA : schema);
        connection.setHostName(hostName);
        connection.setPort(port);
        connection.setPath(path);
        connection.setFileName(fileName);
        connection.setUserName(userName);
        connection.setOverwriteWithoutAsking(overwriteWithoutAsking);
        connection.setPlayerBaseUrl(playerBaseUrl);
        if (previous != null && previous.getPort() == port && Objects.equals(previous.getUserName(), userName)
                && previous.getHostName() != null && previous.getHostName().equalsIgnoreCase(hostName)) {
            connection.setPassword(previous.getPassword());
        }
        return connection;
    }
}

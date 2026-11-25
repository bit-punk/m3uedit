package org.bitpunk.m3uedit.plugins.output.sftp;

import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnectionSettings;

/**
 * The settings of the {@link SftpOutput} stored in project-files.
 *
 * @param sftpConnection the server and the file to write, without password; null while not configured
 */
public record SftpOutputSettings(SftpConnectionSettings sftpConnection) {
}

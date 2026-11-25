package org.bitpunk.m3uedit.plugins.input.sftp;

import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnectionSettings;

/**
 * The settings of the {@link SftpInput} stored in project-files.
 *
 * @param sftpConnection the server and the file to read, without password; null while not configured
 */
public record SftpInputSettings(SftpConnectionSettings sftpConnection) {
}

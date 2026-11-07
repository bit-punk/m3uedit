package org.bitpunk.m3uedit.plugins.output.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.bitpunk.m3uedit.plugins.output.sftp.SftpClient.RemoteEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SftpBrowserDialogTest {

    @Test
    void testParentEntryIsAddedExceptForRoot() {
        List<RemoteEntry> entries = List.of(new RemoteEntry("tv", true), new RemoteEntry("a.m3u", false));

        assertThat(SftpBrowserDialog.withParentEntry("/storage", entries))
                .containsExactly(SftpBrowserDialog.PARENT_ENTRY, entries.get(0), entries.get(1));
        assertThat(SftpBrowserDialog.withParentEntry("/", entries)).containsExactlyElementsOf(entries);
    }

    @Test
    void testDisplayName() {
        assertThat(SftpBrowserDialog.displayName(new RemoteEntry("tv", true))).isEqualTo("tv/");
        assertThat(SftpBrowserDialog.displayName(new RemoteEntry("a.m3u", false))).isEqualTo("a.m3u");
        assertThat(SftpBrowserDialog.displayName(SftpBrowserDialog.PARENT_ENTRY)).isEqualTo(".. (parent directory)");
    }

    @Test
    void testDescribeError() {
        assertThat(SftpBrowserDialog.describeError(new JSchException("Auth fail for methods 'password'")))
                .startsWith("Authentication failed");
        assertThat(SftpBrowserDialog.describeError(new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied")))
                .isEqualTo("Permission denied.");
        assertThat(SftpBrowserDialog.describeError(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "no such file")))
                .isEqualTo("Directory not found.");
        assertThat(SftpBrowserDialog.describeError(new JSchException("timeout: socket is not established")))
                .isEqualTo("Error: timeout: socket is not established");
    }
}

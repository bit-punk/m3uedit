package org.bitpunk.m3uedit.plugins.sftp;

import org.bitpunk.m3uedit.plugins.sftp.SftpClient.RemoteEntry;
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
}

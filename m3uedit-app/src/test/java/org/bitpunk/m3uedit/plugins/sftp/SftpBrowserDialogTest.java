package org.bitpunk.m3uedit.plugins.sftp;

import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.input.sftp.SftpInput;
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
        PluginContext context = new DefaultPluginContext(SftpInput.class);
        assertThat(SftpBrowserDialog.displayName(context, new RemoteEntry("tv", true))).isEqualTo("tv/");
        assertThat(SftpBrowserDialog.displayName(context, new RemoteEntry("a.m3u", false))).isEqualTo("a.m3u");
        assertThat(SftpBrowserDialog.displayName(context, SftpBrowserDialog.PARENT_ENTRY)).isEqualTo(".. (parent directory)");
    }
}

package org.bitpunk.m3uedit.plugins.sftp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SftpConnectionDialogControllerTest {

    @Test
    void testSplitLegacyPath() {
        assertThat(SftpConnectionDialogController.splitLegacyPath("/storage/test.txt")).containsExactly("/storage", "test.txt");
        assertThat(SftpConnectionDialogController.splitLegacyPath("/tv.m3u")).containsExactly("/", "tv.m3u");
        assertThat(SftpConnectionDialogController.splitLegacyPath("/storage/playlists")).containsExactly("/storage/playlists", "");
        assertThat(SftpConnectionDialogController.splitLegacyPath("/storage/")).containsExactly("/storage/", "");
        assertThat(SftpConnectionDialogController.splitLegacyPath(null)).containsExactly("/", "");
    }

    @Test
    void testParsePort() {
        assertThat(SftpConnectionDialogController.parsePort("22")).isEqualTo(22);
        assertThat(SftpConnectionDialogController.parsePort(" 2222 ")).isEqualTo(2222);
        assertThat(SftpConnectionDialogController.parsePort("2a")).isEqualTo(-1);
        assertThat(SftpConnectionDialogController.parsePort("")).isEqualTo(-1);
        assertThat(SftpConnectionDialogController.parsePort(null)).isEqualTo(-1);
    }
}

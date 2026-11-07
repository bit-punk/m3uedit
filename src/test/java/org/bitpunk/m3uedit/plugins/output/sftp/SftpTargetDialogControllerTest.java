package org.bitpunk.m3uedit.plugins.output.sftp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SftpTargetDialogControllerTest {

    @Test
    void testSplitLegacyPath() {
        assertThat(SftpTargetDialogController.splitLegacyPath("/storage/test.txt")).containsExactly("/storage", "test.txt");
        assertThat(SftpTargetDialogController.splitLegacyPath("/tv.m3u")).containsExactly("/", "tv.m3u");
        assertThat(SftpTargetDialogController.splitLegacyPath("/storage/playlists")).containsExactly("/storage/playlists", "");
        assertThat(SftpTargetDialogController.splitLegacyPath("/storage/")).containsExactly("/storage/", "");
        assertThat(SftpTargetDialogController.splitLegacyPath(null)).containsExactly("/", "");
    }

    @Test
    void testParsePort() {
        assertThat(SftpTargetDialogController.parsePort("22")).isEqualTo(22);
        assertThat(SftpTargetDialogController.parsePort(" 2222 ")).isEqualTo(2222);
        assertThat(SftpTargetDialogController.parsePort("2a")).isEqualTo(-1);
        assertThat(SftpTargetDialogController.parsePort("")).isEqualTo(-1);
        assertThat(SftpTargetDialogController.parsePort(null)).isEqualTo(-1);
    }
}

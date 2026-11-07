package org.bitpunk.m3uedit.plugins.output.sftp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SftpClientTest {

    @Test
    void testChild() {
        assertThat(SftpClient.child("/", "storage")).isEqualTo("/storage");
        assertThat(SftpClient.child("/storage", "tv")).isEqualTo("/storage/tv");
        assertThat(SftpClient.child("/storage/", "tv")).isEqualTo("/storage/tv");
    }

    @Test
    void testParent() {
        assertThat(SftpClient.parent("/storage/tv")).isEqualTo("/storage");
        assertThat(SftpClient.parent("/storage/tv/")).isEqualTo("/storage");
        assertThat(SftpClient.parent("/storage")).isEqualTo("/");
        assertThat(SftpClient.parent("/")).isEqualTo("/");
        assertThat(SftpClient.parent(null)).isEqualTo("/");
    }
}

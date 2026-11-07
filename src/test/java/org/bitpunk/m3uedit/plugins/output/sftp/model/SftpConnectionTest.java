package org.bitpunk.m3uedit.plugins.output.sftp.model;

import org.bitpunk.m3uedit.util.PojoValidator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

class SftpConnectionTest {

    @Test
    void testValidConnection() throws URISyntaxException {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("192.168.1.1");
        connection.setPort(22);
        connection.setUserName("admin");
        connection.setPassword("pass");
        connection.setPath("/etc/playlist.m3u");

        PojoValidator<SftpConnection> validator = new PojoValidator<>();
        assertThat(validator.validate(connection)).isEmpty();
        assertThat(connection.isValidUri()).isTrue();

        URI uri = connection.toURI();
        assertThat(uri.getScheme()).isEqualTo("sftp");
        assertThat(uri.getUserInfo()).isEqualTo("admin");
        assertThat(uri.toString()).doesNotContain("pass");
        assertThat(uri.getHost()).isEqualTo("192.168.1.1");
        assertThat(uri.getPort()).isEqualTo(22);
        assertThat(uri.getPath()).isEqualTo("/etc/playlist.m3u");
    }

    @Test
    void testToStringDoesNotContainPassword() {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("example.com");
        connection.setUserName("admin");
        connection.setPassword("topSecret42");

        assertThat(connection.toString()).contains("example.com").doesNotContain("topSecret42");
    }

    @Test
    void testInvalidConnection() {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("");
        connection.setPort(-1);
        connection.setUserName("");
        connection.setPassword("");
        connection.setPath("invalid/path/without/leading/slash");

        PojoValidator<SftpConnection> validator = new PojoValidator<>();
        assertThat(validator.validate(connection)).isNotEmpty();
    }

    @Test
    void testTargetPathJoinsDirectoryAndFileName() {
        SftpConnection connection = new SftpConnection();
        connection.setPath("/storage");
        connection.setFileName("tv.m3u");
        assertThat(connection.isLegacyPath()).isFalse();
        assertThat(connection.getTargetPath()).isEqualTo("/storage/tv.m3u");

        connection.setPath("/storage/");
        assertThat(connection.getTargetPath()).isEqualTo("/storage/tv.m3u");

        connection.setPath("/");
        assertThat(connection.getTargetPath()).isEqualTo("/tv.m3u");
    }

    @Test
    void testTargetPathUsesDefaultFileNameWhenEmpty() {
        SftpConnection connection = new SftpConnection();
        connection.setPath("/storage");
        connection.setFileName(" ");
        assertThat(connection.getTargetPath()).isEqualTo("/storage/" + SftpConnection.DEFAULT_FILE_NAME);
    }

    @Test
    void testLegacyConnectionKeepsConfiguredPath() {
        SftpConnection connection = new SftpConnection();
        connection.setPath("/storage/test.txt");
        assertThat(connection.isLegacyPath()).isTrue();
        assertThat(connection.getTargetPath()).isEqualTo("/storage/test.txt");
    }

    @Test
    void testFileNameMustNotContainSlash() {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("example.com");
        connection.setPort(22);
        connection.setUserName("user");
        connection.setPath("/storage");
        connection.setFileName("sub/tv.m3u");

        assertThat(new PojoValidator<SftpConnection>().validate(connection))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("fileName");
    }

    @Test
    void testUriContainsFileName() throws URISyntaxException {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("example.com");
        connection.setPort(22);
        connection.setUserName("user");
        connection.setPath("/storage");
        connection.setFileName("tv.m3u");
        assertThat(connection.toURI()).hasToString("sftp://user@example.com:22/storage/tv.m3u");
    }
}

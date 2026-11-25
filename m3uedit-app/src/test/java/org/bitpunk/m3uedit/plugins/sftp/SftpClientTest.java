package org.bitpunk.m3uedit.plugins.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.input.sftp.SftpInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SftpClientTest {
    private static final PluginContext CONTEXT = new DefaultPluginContext(SftpInput.class);

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

    @Test
    void testIsAuthenticationFailure() {
        assertThat(SftpClient.isAuthenticationFailure(new JSchException("Auth fail for methods 'password'"))).isTrue();
        assertThat(SftpClient.isAuthenticationFailure(new JSchException("reject HostKey: example.com"))).isFalse();
        assertThat(SftpClient.isAuthenticationFailure(new JSchException())).isFalse();
    }

    @Test
    void testDescribeError() {
        assertThat(SftpClient.describeError(CONTEXT, new JSchException("Auth fail for methods 'password'")))
                .startsWith("Authentication failed");
        assertThat(SftpClient.describeError(CONTEXT, new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied")))
                .isEqualTo("Permission denied.");
        assertThat(SftpClient.describeError(CONTEXT, new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "no such file")))
                .isEqualTo("Directory not found.");
        assertThat(SftpClient.describeError(CONTEXT, new JSchException("timeout: socket is not established")))
                .isEqualTo("Error: timeout: socket is not established");
    }
}

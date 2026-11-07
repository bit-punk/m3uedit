package org.bitpunk.m3uedit.plugins.sftp;

import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SftpUserInteractionTest {

    @Test
    void testEnsurePasswordUsesExistingPasswordWithoutPrompt() {
        SftpConnection connection = createConnection();
        connection.setPassword("known");
        CountingInteraction interaction = new CountingInteraction(Optional.of("other"));

        assertThat(interaction.ensurePassword(connection)).isTrue();
        assertThat(interaction.calls.get()).isZero();
        assertThat(connection.getPassword()).isEqualTo("known");
    }

    @Test
    void testEnsurePasswordPromptsWhenMissing() {
        SftpConnection connection = createConnection();
        CountingInteraction interaction = new CountingInteraction(Optional.of("entered"));

        assertThat(interaction.ensurePassword(connection)).isTrue();
        assertThat(interaction.calls.get()).isOne();
        assertThat(connection.getPassword()).isEqualTo("entered");
    }

    @Test
    void testEnsurePasswordCancelled() {
        SftpConnection connection = createConnection();

        assertThat(new CountingInteraction(Optional.empty()).ensurePassword(connection)).isFalse();
        assertThat(connection.getPassword()).isNull();
    }

    private static SftpConnection createConnection() {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("example.com");
        connection.setPort(22);
        connection.setUserName("user");
        connection.setPath("/playlists");
        connection.setFileName("tv.m3u");
        return connection;
    }

    private static class CountingInteraction extends SftpUserInteraction {
        private final Optional<String> answer;
        private final AtomicInteger calls = new AtomicInteger();

        CountingInteraction(Optional<String> answer) {
            this.answer = answer;
        }

        @Override
        public Optional<String> requestPassword(String userName, String hostName, int port) {
            calls.incrementAndGet();
            return answer;
        }
    }
}

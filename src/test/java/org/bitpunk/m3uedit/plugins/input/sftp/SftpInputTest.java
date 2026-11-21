package org.bitpunk.m3uedit.plugins.input.sftp;

import org.bitpunk.m3uedit.plugins.input.InputReadException;
import org.bitpunk.m3uedit.plugins.sftp.SftpUserInteraction;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpInputTest {

    private static SftpConnection createConnection() {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("192.168.178.22");
        connection.setPort(22);
        connection.setUserName("root");
        connection.setPassword("topSecret42");
        connection.setPath("/storage");
        connection.setFileName("tv.m3u");
        return connection;
    }

    @Test
    void testNameMatchesInputType() {
        assertThat(new SftpInput().getName()).isEqualTo("SFTP Input");
    }

    @Test
    void testSourceWithoutPassword() {
        SftpInput sftpInput = new SftpInput();
        assertThat(sftpInput.getSource()).isNull();

        sftpInput.setSftpConnection(createConnection());
        assertThat(sftpInput.getSource())
                .isEqualTo("sftp://root@192.168.178.22:22/storage/tv.m3u")
                .doesNotContain("topSecret42");
    }

    @Test
    void testToStringDoesNotContainPassword() {
        SftpInput sftpInput = new SftpInput();
        sftpInput.setSftpConnection(createConnection());
        assertThat(sftpInput.toString()).isEqualTo("SftpInput[SFTP Input]").doesNotContain("topSecret42");
        // the connection itself shows its host, but never the password
        assertThat(sftpInput.getSftpConnection().toString()).contains("192.168.178.22").doesNotContain("topSecret42");
    }

    @Test
    void testReadWithoutConnectionReturnsEmptyList() {
        assertThat(new SftpInput().read()).isEmpty();
    }

    @Test
    void testNotReadWhenPasswordPromptCancelled() {
        AtomicInteger prompts = new AtomicInteger();
        SftpInput sftpInput = new SftpInput() {
            @Override
            protected SftpUserInteraction createUserInteraction() {
                return new SftpUserInteraction() {
                    @Override
                    public Optional<String> requestPassword(String userName, String hostName, int port) {
                        prompts.incrementAndGet();
                        return Optional.empty();
                    }
                };
            }
        };
        SftpConnection connection = createConnection();
        connection.setPassword(null);
        sftpInput.setSftpConnection(connection);

        // reported as error of the input, without a dialog of its own
        assertThatThrownBy(sftpInput::read).isInstanceOf(InputReadException.class)
                .hasMessageContaining("no password entered for root@192.168.178.22");
        assertThat(prompts.get()).isOne();
    }
}

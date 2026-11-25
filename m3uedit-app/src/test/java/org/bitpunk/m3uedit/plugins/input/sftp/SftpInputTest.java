package org.bitpunk.m3uedit.plugins.input.sftp;

import org.bitpunk.m3uedit.api.InputReadException;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.plugins.sftp.SftpUserInteraction;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnectionSettings;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(TestPluginSupport.create(SftpInput::new).getName()).isEqualTo("SFTP Input");
    }

    @Test
    void testSourceWithoutPassword() {
        SftpInput sftpInput = TestPluginSupport.create(SftpInput::new);
        assertThat(sftpInput.getSource()).isNull();

        sftpInput.setSftpConnection(createConnection());
        assertThat(sftpInput.getSource())
                .isEqualTo("sftp://root@192.168.178.22:22/storage/tv.m3u")
                .doesNotContain("topSecret42");
    }

    @Test
    void testToStringDoesNotContainPassword() {
        SftpInput sftpInput = TestPluginSupport.create(SftpInput::new);
        sftpInput.setSftpConnection(createConnection());
        assertThat(sftpInput.toString()).isEqualTo("SftpInput[SFTP Input]").doesNotContain("topSecret42");
        // the connection itself shows its host, but never the password
        assertThat(sftpInput.getSftpConnection().toString()).contains("192.168.178.22").doesNotContain("topSecret42");
    }

    @Test
    void testReadWithoutConnectionReturnsEmptyList() throws Exception {
        assertThat(TestPluginSupport.create(SftpInput::new).read().entries()).isEmpty();
    }

    @Test
    void testNotReadWhenPasswordPromptCancelled() {
        AtomicInteger prompts = new AtomicInteger();
        PluginContext context = new DefaultPluginContext(SftpInput.class);
        SftpInput sftpInput = TestPluginSupport.create(() -> new SftpInput() {
            @Override
            protected SftpUserInteraction createUserInteraction() {
                return new SftpUserInteraction(context) {
                    @Override
                    public Optional<String> requestPassword(String userName, String hostName, int port) {
                        prompts.incrementAndGet();
                        return Optional.empty();
                    }
                };
            }
        });
        SftpConnection connection = createConnection();
        connection.setPassword(null);
        sftpInput.setSftpConnection(connection);

        // reported as error of the input, without a dialog of its own
        assertThatThrownBy(sftpInput::read).isInstanceOf(InputReadException.class)
                .hasMessageContaining("no password entered for root@192.168.178.22");
        assertThat(prompts.get()).isOne();
    }

    @Test
    void testSettingsRoundTripWithoutPassword() {
        SftpInput sftpInput = TestPluginSupport.create(SftpInput::new);
        sftpInput.setSftpConnection(createConnection());

        SftpInputSettings settings = (SftpInputSettings) sftpInput.getSettings();
        assertThat(settings.sftpConnection()).isEqualTo(new SftpConnectionSettings("sftp", "192.168.178.22", 22,
                "/storage", "tv.m3u", "root", false, null));
        assertThat(settings.toString()).doesNotContain("topSecret42");

        SftpInput loaded = TestPluginSupport.create(SftpInput::new);
        loaded.setSettings(settings);
        assertThat(loaded.getSource()).isEqualTo("sftp://root@192.168.178.22:22/storage/tv.m3u");
        assertThat(loaded.getSftpConnection().getPassword()).isNull();
        assertThat(loaded.getSettings()).isEqualTo(settings);
    }

    @Test
    void testThePasswordInMemoryIsKeptForTheSameLogin() {
        SftpInput sftpInput = TestPluginSupport.create(SftpInput::new);
        sftpInput.setSftpConnection(createConnection());

        sftpInput.setSettings(new SftpInputSettings(new SftpConnectionSettings("sftp", "192.168.178.22", 22,
                "/other", "radio.m3u", "root", false, null)));
        assertThat(sftpInput.getSftpConnection().getPassword()).isEqualTo("topSecret42");

        sftpInput.setSettings(new SftpInputSettings(new SftpConnectionSettings("sftp", "nas.local", 22,
                "/other", "radio.m3u", "root", false, null)));
        assertThat(sftpInput.getSftpConnection().getPassword()).isNull();
    }

    @Test
    void testDefaultsForMissingSettings() {
        SftpInput sftpInput = TestPluginSupport.create(SftpInput::new);
        sftpInput.setSftpConnection(createConnection());

        sftpInput.setSettings(new SftpInputSettings(null));
        assertThat(sftpInput.getSftpConnection()).isNull();
        assertThat(sftpInput.getSource()).isNull();

        sftpInput.setSettings(new SftpInputSettings(new SftpConnectionSettings(null, "nas", 22, "/tv.m3u", null,
                "root", false, null)));
        assertThat(sftpInput.getSftpConnection().getSchema()).isEqualTo("sftp");
        assertThat(sftpInput.getSftpConnection().isLegacyPath()).isTrue();
        assertThat(sftpInput.getSource()).isEqualTo("sftp://root@nas:22/tv.m3u");
    }

    @Test
    void testProjectFileOfTodayIsRead(@TempDir Path tempDir) throws Exception {
        Path projectFile = tempDir.resolve("sftp.m3uproj");
        Files.writeString(projectFile, """
                ---
                name: test
                inputs:
                  - type: SftpInput
                    enabled: true
                    sftpConnection:
                      schema: sftp
                      hostName: example.com
                      port: 22
                      path: /playlists
                      fileName: tv.m3u
                      userName: user
                      overwriteWithoutAsking: false
                      password: manuallyAdded
                """);

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        SftpInput sftpInput = (SftpInput) loaded.getInputs().getFirst();
        assertThat(PluginStates.enabled(sftpInput).get()).isTrue();
        assertThat(sftpInput.getSource()).isEqualTo("sftp://user@example.com:22/playlists/tv.m3u");
        // a password in the project-file is never used
        assertThat(sftpInput.getSftpConnection().getPassword()).isNull();
    }
}

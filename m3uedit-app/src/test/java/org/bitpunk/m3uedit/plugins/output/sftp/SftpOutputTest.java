package org.bitpunk.m3uedit.plugins.output.sftp;

import org.bitpunk.m3uedit.api.Export;
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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SftpOutputTest {

    @Test
    void testResolveTargetPath() {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);

        assertThat(sftpOutput.resolveTargetPath(null, null)).isEqualTo(SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "")).isEqualTo(SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "/")).isEqualTo("/" + SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "/var/playlists/")).isEqualTo("/var/playlists/" + SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "/var/playlists/custom.m3u")).isEqualTo("/var/playlists/custom.m3u");
    }

    @Test
    void testGetOutput() {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);
        assertThat(sftpOutput.getOutput()).isNull();

        SftpConnection connection = new SftpConnection();
        connection.setHostName("example.com");
        connection.setPort(22);
        connection.setUserName("user");
        connection.setPassword("secret");
        connection.setPath("/playlists/tv.m3u");

        sftpOutput.setSftpConnection(connection);
        URI uri = sftpOutput.getOutput();
        assertThat(uri).isNotNull();
        assertThat(uri.toString()).isEqualTo("sftp://user@example.com:22/playlists/tv.m3u");
    }

    @Test
    void testWriteNullOrEmptyPlaylistDoesNotThrow() {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);
        sftpOutput.write(Export.of(null));
        sftpOutput.write(Export.of(List.of()));
    }

    @Test
    void testEnsureParentDirectoriesExistNullChannelDoesNotThrow() {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);
        sftpOutput.ensureParentDirectoriesExist(null, null);
        sftpOutput.ensureParentDirectoriesExist(null, "playlist.m3u");
        sftpOutput.ensureParentDirectoriesExist(null, "/var/playlists/playlist.m3u");
    }

    @Test
    void testGetSftpErrorName() {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);
        assertThat(sftpOutput.getSftpErrorName(0)).isEqualTo("SSH_FX_OK (0)");
        assertThat(sftpOutput.getSftpErrorName(1)).isEqualTo("SSH_FX_EOF (1)");
        assertThat(sftpOutput.getSftpErrorName(2)).isEqualTo("SSH_FX_NO_SUCH_FILE (2)");
        assertThat(sftpOutput.getSftpErrorName(3)).isEqualTo("SSH_FX_PERMISSION_DENIED (3)");
        assertThat(sftpOutput.getSftpErrorName(4)).isEqualTo("SSH_FX_FAILURE (4)");
        assertThat(sftpOutput.getSftpErrorName(99)).isEqualTo("SSH_FX_UNKNOWN (99)");
    }

    @Test
    void testBuildContextDetailsWithoutChannel() {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);
        SftpConnection connection = new SftpConnection();
        connection.setHostName("myserver.local");
        connection.setPort(2222);
        connection.setUserName("sftpuser");
        connection.setPassword("mypassword");
        connection.setPath("/remote/dir/playlist.m3u");
        sftpOutput.setSftpConnection(connection);

        String sftpDetails = sftpOutput.buildSftpContextDetails(null, "/remote/dir/playlist.m3u", 10, 1024);
        assertThat(sftpDetails).contains("host=myserver.local");
        assertThat(sftpDetails).contains("port=2222");
        assertThat(sftpDetails).contains("user=sftpuser");
        assertThat(sftpDetails).contains("configuredPath=/remote/dir/playlist.m3u");
        assertThat(sftpDetails).contains("targetPath=/remote/dir/playlist.m3u");
        assertThat(sftpDetails).contains("playlistEntries=10");
        assertThat(sftpDetails).contains("payloadBytes=1024");
        assertThat(sftpDetails).contains("channelConnected=false");
        // Ensure password is not leaked in context string
        assertThat(sftpDetails).doesNotContain("mypassword");

        String jschDetails = sftpOutput.buildJschContextDetails(null, "/remote/dir/playlist.m3u", 10, 1024);
        assertThat(jschDetails).contains("host=myserver.local");
        assertThat(jschDetails).doesNotContain("mypassword");
    }

    private static SftpConnection createConnection() {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("example.com");
        connection.setPort(22);
        connection.setUserName("user");
        connection.setPath("/playlists/tv.m3u");
        return connection;
    }


    @Test
    void testOverwriteCancelled() {
        SftpOutput sftpOutput = outputWith(createConnection());
        OverwriteInteraction interaction = new OverwriteInteraction(SftpUserInteraction.OverwriteDecision.CANCEL);

        assertThat(sftpOutput.confirmOverwriteIfExists("/playlists/tv.m3u", "Existing file: 10 bytes", interaction)).isFalse();
        assertThat(interaction.calls.get()).isOne();
        assertThat(interaction.lastTarget).isEqualTo("user@example.com:22/playlists/tv.m3u");
        assertThat(sftpOutput.getSftpConnection().isOverwriteWithoutAsking()).isFalse();
    }

    @Test
    void testOverwriteOnceKeepsAsking() {
        SftpOutput sftpOutput = outputWith(createConnection());
        OverwriteInteraction interaction = new OverwriteInteraction(SftpUserInteraction.OverwriteDecision.OVERWRITE);

        assertThat(sftpOutput.confirmOverwriteIfExists("/playlists/tv.m3u", "Existing file: 10 bytes", interaction)).isTrue();
        assertThat(sftpOutput.getSftpConnection().isOverwriteWithoutAsking()).isFalse();
    }

    @Test
    void testOverwriteAlwaysStopsAsking() {
        SftpOutput sftpOutput = outputWith(createConnection());
        OverwriteInteraction interaction = new OverwriteInteraction(SftpUserInteraction.OverwriteDecision.OVERWRITE_ALWAYS);

        assertThat(sftpOutput.confirmOverwriteIfExists("/playlists/tv.m3u", "Existing file: 10 bytes", interaction)).isTrue();
        assertThat(sftpOutput.getSftpConnection().isOverwriteWithoutAsking()).isTrue();
        assertThat(sftpOutput.confirmOverwriteIfExists("/playlists/tv.m3u", "Existing file: 10 bytes", interaction)).isTrue();
        assertThat(interaction.calls.get()).isOne();
    }

    private static SftpOutput outputWith(SftpConnection connection) {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);
        sftpOutput.setSftpConnection(connection);
        return sftpOutput;
    }

    private static class OverwriteInteraction extends SftpUserInteraction {
        private final OverwriteDecision decision;
        private final AtomicInteger calls = new AtomicInteger();
        private String lastTarget;

        OverwriteInteraction(OverwriteDecision decision) {
            super(new DefaultPluginContext(SftpOutput.class));
            this.decision = decision;
        }

        @Override
        public OverwriteDecision confirmOverwrite(String target, String existingFileInfo) {
            calls.incrementAndGet();
            lastTarget = target;
            return decision;
        }
    }

    @Test
    void testPlayerUrlOfAdditionalFiles() {
        SftpOutput sftpOutput = TestPluginSupport.create(SftpOutput::new);
        SftpConnection connection = new SftpConnection();
        sftpOutput.setSftpConnection(connection);

        assertThat(sftpOutput.playerUrlOf("tv.xml.gz")).as("no base url configured").isNull();
        connection.setPlayerBaseUrl("http://nas.local/iptv");
        assertThat(sftpOutput.playerUrlOf("tv.xml.gz")).isEqualTo("http://nas.local/iptv/tv.xml.gz");
        connection.setPlayerBaseUrl(" http://nas.local/iptv/ ");
        assertThat(sftpOutput.playerUrlOf("tv.xml.gz")).isEqualTo("http://nas.local/iptv/tv.xml.gz");
        assertThat(sftpOutput.playerUrlOf(null)).as("file not written").isNull();
    }

    @Test
    void testSettingsRoundTripWithoutPassword() {
        SftpOutput sftpOutput = outputWith(createConnection());
        sftpOutput.getSftpConnection().setPassword("topSecret42");
        sftpOutput.getSftpConnection().setOverwriteWithoutAsking(true);
        sftpOutput.getSftpConnection().setPlayerBaseUrl("http://nas.local/iptv/");

        SftpOutputSettings settings = (SftpOutputSettings) sftpOutput.getSettings();
        assertThat(settings.sftpConnection()).isEqualTo(new SftpConnectionSettings("sftp", "example.com", 22,
                "/playlists/tv.m3u", null, "user", true, "http://nas.local/iptv/"));
        assertThat(settings.toString()).doesNotContain("topSecret42");

        SftpOutput loaded = TestPluginSupport.create(SftpOutput::new);
        loaded.setSettings(settings);
        assertThat(loaded.getOutput()).hasToString("sftp://user@example.com:22/playlists/tv.m3u");
        assertThat(loaded.getSftpConnection().isOverwriteWithoutAsking()).isTrue();
        assertThat(loaded.getSftpConnection().getPlayerBaseUrl()).isEqualTo("http://nas.local/iptv/");
        assertThat(loaded.getSftpConnection().getPassword()).isNull();
        assertThat(loaded.getSettings()).isEqualTo(settings);
    }

    @Test
    void testDefaultsForMissingSettings() {
        SftpOutput sftpOutput = outputWith(createConnection());

        sftpOutput.setSettings(new SftpOutputSettings(null));

        assertThat(sftpOutput.getSftpConnection()).isNull();
        assertThat(sftpOutput.getOutput()).isNull();
    }

    @Test
    void testProjectFileOfTodayIsRead(@TempDir Path tempDir) throws Exception {
        Path projectFile = tempDir.resolve("sftp.m3uproj");
        Files.writeString(projectFile, """
                ---
                name: test
                outputs:
                  - type: SftpOutput
                    enabled: true
                    sftpConnection:
                      schema: sftp
                      hostName: example.com
                      port: 22
                      path: /playlists
                      fileName: tv.m3u
                      userName: user
                      overwriteWithoutAsking: true
                      playerBaseUrl: http://nas.local/iptv/
                """);

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        SftpOutput sftpOutput = (SftpOutput) loaded.getOutputs().getFirst();
        assertThat(PluginStates.enabled(sftpOutput).get()).isTrue();
        assertThat(sftpOutput.getOutput()).hasToString("sftp://user@example.com:22/playlists/tv.m3u");
        assertThat(sftpOutput.getSftpConnection().isOverwriteWithoutAsking()).isTrue();
        assertThat(sftpOutput.getSftpConnection().getPlayerBaseUrl()).isEqualTo("http://nas.local/iptv/");
        assertThat(sftpOutput.getSftpConnection().getPassword()).isNull();
    }
}

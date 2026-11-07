package org.bitpunk.m3uedit.plugins.output.sftp;

import org.bitpunk.m3uedit.plugins.sftp.SftpUserInteraction;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SftpOutputTest {

    @Test
    void testResolveTargetPath() {
        SftpOutput sftpOutput = new SftpOutput();

        assertThat(sftpOutput.resolveTargetPath(null, null)).isEqualTo(SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "")).isEqualTo(SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "/")).isEqualTo("/" + SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "/var/playlists/")).isEqualTo("/var/playlists/" + SftpOutput.DEFAULT_PLAYLIST_FILENAME);
        assertThat(sftpOutput.resolveTargetPath(null, "/var/playlists/custom.m3u")).isEqualTo("/var/playlists/custom.m3u");
    }

    @Test
    void testGetOutput() {
        SftpOutput sftpOutput = new SftpOutput();
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
        SftpOutput sftpOutput = new SftpOutput();
        sftpOutput.write(null);
        sftpOutput.write(Collections.emptyList());
    }

    @Test
    void testEnsureParentDirectoriesExistNullChannelDoesNotThrow() {
        SftpOutput sftpOutput = new SftpOutput();
        sftpOutput.ensureParentDirectoriesExist(null, null);
        sftpOutput.ensureParentDirectoriesExist(null, "playlist.m3u");
        sftpOutput.ensureParentDirectoriesExist(null, "/var/playlists/playlist.m3u");
    }

    @Test
    void testGetSftpErrorName() {
        SftpOutput sftpOutput = new SftpOutput();
        assertThat(sftpOutput.getSftpErrorName(0)).isEqualTo("SSH_FX_OK (0)");
        assertThat(sftpOutput.getSftpErrorName(1)).isEqualTo("SSH_FX_EOF (1)");
        assertThat(sftpOutput.getSftpErrorName(2)).isEqualTo("SSH_FX_NO_SUCH_FILE (2)");
        assertThat(sftpOutput.getSftpErrorName(3)).isEqualTo("SSH_FX_PERMISSION_DENIED (3)");
        assertThat(sftpOutput.getSftpErrorName(4)).isEqualTo("SSH_FX_FAILURE (4)");
        assertThat(sftpOutput.getSftpErrorName(99)).isEqualTo("SSH_FX_UNKNOWN (99)");
    }

    @Test
    void testBuildContextDetailsWithoutChannel() {
        SftpOutput sftpOutput = new SftpOutput();
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
        SftpOutput sftpOutput = new SftpOutput();
        sftpOutput.setSftpConnection(connection);
        return sftpOutput;
    }

    private static class OverwriteInteraction extends SftpUserInteraction {
        private final OverwriteDecision decision;
        private final AtomicInteger calls = new AtomicInteger();
        private String lastTarget;

        OverwriteInteraction(OverwriteDecision decision) {
            this.decision = decision;
        }

        @Override
        public OverwriteDecision confirmOverwrite(String target, String existingFileInfo) {
            calls.incrementAndGet();
            lastTarget = target;
            return decision;
        }
    }
}

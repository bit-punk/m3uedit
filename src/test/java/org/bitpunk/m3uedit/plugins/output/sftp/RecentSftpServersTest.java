package org.bitpunk.m3uedit.plugins.output.sftp;

import org.bitpunk.m3uedit.plugins.output.sftp.model.RecentSftpServer;
import org.bitpunk.m3uedit.plugins.output.sftp.model.SftpConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentSftpServersTest {
    @TempDir
    Path tempDir;

    private RecentSftpServers createStore() {
        return new RecentSftpServers(tempDir.resolve("plugins").resolve(RecentSftpServers.CONFIG_FILE_NAME));
    }

    private static SftpConnection connection(String host, String path, String fileName) {
        SftpConnection connection = new SftpConnection();
        connection.setHostName(host);
        connection.setPort(22);
        connection.setUserName("root");
        connection.setPassword("topSecret42");
        connection.setPath(path);
        connection.setFileName(fileName);
        return connection;
    }

    @Test
    void testEmptyWhenNoFileExists() {
        assertThat(createStore().getServers()).isEmpty();
    }

    @Test
    void testRecordSuccessIsPersistedWithoutPassword() throws Exception {
        createStore().recordSuccess(connection("192.168.178.22", "/storage", "tv.m3u"));

        List<RecentSftpServer> servers = createStore().getServers();
        assertThat(servers).hasSize(1);
        assertThat(servers.getFirst().displayName()).isEqualTo("root@192.168.178.22:22/storage/tv.m3u");
        String yaml = Files.readString(tempDir.resolve("plugins").resolve(RecentSftpServers.CONFIG_FILE_NAME));
        assertThat(yaml).contains("192.168.178.22").doesNotContain("topSecret42").doesNotContain("password");
    }

    @Test
    void testMostRecentFirstAndNoDuplicates() {
        RecentSftpServers store = createStore();
        store.recordSuccess(connection("a.local", "/storage", "tv.m3u"));
        store.recordSuccess(connection("b.local", "/storage", "tv.m3u"));
        // same target again (host case and trailing slash differ) -> moves to the top
        store.recordSuccess(connection("A.local", "/storage/", "tv.m3u"));

        assertThat(store.getServers()).extracting(RecentSftpServer::getHostName).containsExactly("A.local", "b.local");
    }

    @Test
    void testDifferentTargetsOnSameServerAreSeparateEntries() {
        RecentSftpServers store = createStore();
        store.recordSuccess(connection("a.local", "/storage", "tv.m3u"));
        store.recordSuccess(connection("a.local", "/storage", "radio.m3u"));

        assertThat(store.getServers()).extracting(RecentSftpServer::getFileName).containsExactly("radio.m3u", "tv.m3u");
    }

    @Test
    void testListIsLimited() {
        RecentSftpServers store = createStore();
        for (int i = 0; i < RecentSftpServers.MAX_ENTRIES + 3; i++) {
            store.recordSuccess(connection("host" + i, "/storage", "tv.m3u"));
        }
        List<RecentSftpServer> servers = store.getServers();
        assertThat(servers).hasSize(RecentSftpServers.MAX_ENTRIES);
        assertThat(servers.getFirst().getHostName()).isEqualTo("host" + (RecentSftpServers.MAX_ENTRIES + 2));
    }

    @Test
    void testRemove() {
        RecentSftpServers store = createStore();
        store.recordSuccess(connection("a.local", "/storage", "tv.m3u"));
        store.recordSuccess(connection("b.local", "/storage", "tv.m3u"));

        store.remove(store.getServers().getFirst());

        assertThat(store.getServers()).extracting(RecentSftpServer::getHostName).containsExactly("a.local");
    }

    @Test
    void testToConnectionRestoresTarget() {
        RecentSftpServer server = RecentSftpServer.of(connection("a.local", "/storage", "tv.m3u"));
        SftpConnection restored = server.toConnection();
        assertThat(restored.getTargetPath()).isEqualTo("/storage/tv.m3u");
        assertThat(restored.getUserName()).isEqualTo("root");
        assertThat(restored.getPassword()).isNull();
    }
}

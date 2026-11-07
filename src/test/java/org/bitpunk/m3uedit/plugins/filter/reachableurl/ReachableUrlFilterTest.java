package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.reachableurl.model.ReachableUrlFilterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReachableUrlFilterTest {

    @TempDir
    Path configDir;
    private Path configFile;
    private ReachableUrlFilter filter;
    private ServerSocket openServer;
    private int closedPort;

    @BeforeEach
    void setUp() throws IOException {
        configFile = configDir.resolve(ReachableUrlFilter.CONFIG_FILE_NAME);
        filter = new ReachableUrlFilter(new YamlConfigManager<>(configFile, ReachableUrlFilter.RESOURCE_PATH,
                ReachableUrlFilterConfig.class, ReachableUrlFilterConfig::new));
        openServer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        try (ServerSocket closed = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            closedPort = closed.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        openServer.close();
    }

    private String openUrl(String path) {
        return "http://127.0.0.1:%d/%s".formatted(openServer.getLocalPort(), path);
    }

    private String closedUrl(String path) {
        return "http://127.0.0.1:%d/%s".formatted(closedPort, path);
    }

    private static Entry entry(String name, String uri) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri(uri);
        return entry;
    }

    @Test
    void testReachableHost() {
        assertThat(filter.isUrlReachable(openUrl("stream.ts"), ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS)).isTrue();
    }

    @Test
    void testUnreachableHost() {
        assertThat(filter.isUrlReachable(closedUrl("stream.ts"), ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "http://bad url with blanks", "not-a-url", "unknown://127.0.0.1/stream"})
    void testInvalidUrlsAreNotReachableAndDoNotThrow(String url) {
        assertThat(filter.isUrlReachable(url, ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS)).isFalse();
    }

    @Test
    void testFileUrls(@TempDir Path tempDir) throws IOException {
        Path existing = Files.createFile(tempDir.resolve("stream.ts"));
        Path missing = tempDir.resolve("missing.ts");

        assertThat(filter.isUrlReachable(existing.toUri().toString(), 100)).isTrue();
        assertThat(filter.isUrlReachable(missing.toUri().toString(), 100)).isFalse();
    }

    @Test
    void testFilterKeepsOrderAndRemovesUnreachableEntries() {
        List<Entry> entries = List.of(
                entry("a", openUrl("a")),
                entry("b", closedUrl("b")),
                entry("c", "http://bad url"),
                entry("d", openUrl("d")),
                entry("e", null),
                entry("f", openUrl("f")));

        assertThat(filter.filter(entries)).extracting(Entry::getName).containsExactly("a", "d", "f");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "http://www.google.de, 1000, true",
            "http://192.168.1.1:8080, 100, false"
    })
    @Disabled("requires internet access, will not run properly on github")
    void testRemoteURIs(String url, int timeout, boolean expectedReachability) {
        assertThat(filter.isUrlReachable(url, timeout)).isEqualTo(expectedReachability);
    }

    @Test
    void testDefaultConfigIsCreatedOnFirstUse() {
        assertThat(configFile).doesNotExist();

        assertThat(filter.getTimeoutMillis()).isEqualTo(ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS);
        assertThat(configFile).exists();
    }

    @Test
    void testConfiguredTimeoutIsUsedAndReloaded() throws IOException {
        Files.writeString(configFile, "timeoutMillis: 500\n");
        assertThat(filter.getTimeoutMillis()).isEqualTo(500);

        // changes take effect without creating a new filter
        Files.writeString(configFile, "timeoutMillis: 7500\n");
        assertThat(filter.getTimeoutMillis()).isEqualTo(7500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeoutMillis: 0", "timeoutMillis: -100", "timeoutMillis: not-a-number"})
    void testInvalidTimeoutFallsBackToDefault(String content) throws IOException {
        Files.writeString(configFile, content + "\n");
        assertThat(filter.getTimeoutMillis()).isEqualTo(ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS);
    }

    @Test
    void testFilterInstancesWithDifferentConfigsAreEqual() {
        assertThat(filter).isEqualTo(new ReachableUrlFilter(null));
    }
}

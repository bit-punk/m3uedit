package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import com.sun.net.httpserver.HttpServer;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ReachableUrlFilterTest {
    private static final int TIMEOUT = 1000;

    @TempDir
    Path configDir;
    private Path configFile;
    private ReachableUrlFilter filter;
    private HttpServer httpServer;
    private final Map<String, String> userAgents = new ConcurrentHashMap<>();
    /** accepts connections, but never answers */
    private ServerSocket silentServer;
    private int closedPort;

    @BeforeEach
    void setUp() throws IOException {
        configFile = configDir.resolve(ReachableUrlFilter.CONFIG_FILE_NAME);
        filter = new ReachableUrlFilter(new YamlConfigManager<>(configFile, ReachableUrlFilter.RESOURCE_PATH,
                ReachableUrlFilterConfig.class, ReachableUrlFilterConfig::new));
        silentServer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        try (ServerSocket closed = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            closedPort = closed.getLocalPort();
        }
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serve("/stream.ts", 200, "video/mp2t", "\u0047 mpeg-ts data");
        serve("/empty.ts", 200, "video/mp2t", "");
        serve("/error.html", 200, "text/html", "<html>stream offline</html>");
        serve("/forbidden.ts", 403, "text/plain", "forbidden");
        redirect("/redirect.ts", "/stream.ts");
        redirect("/loop.ts", "/loop.ts");
        serve("/master.m3u8", 200, "application/vnd.apple.mpegurl",
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvariant/media.m3u8\n");
        serve("/variant/media.m3u8", 200, "application/vnd.apple.mpegurl",
                "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\n../stream.ts\n");
        serve("/broken.m3u8", 200, "application/x-mpegurl",
                "#EXTM3U\n#EXTINF:10,\nmissing.ts\n");
        serve("/nostreams.m3u8", 200, "text/plain", "#EXTM3U\n#EXT-X-ENDLIST\n");
        httpServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        httpServer.stop(0);
        silentServer.close();
    }

    private void serve(String path, int status, String contentType, String content) {
        httpServer.createContext(path, exchange -> {
            if (!exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            userAgents.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
            byte[] body = content.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private void redirect(String path, String target) {
        httpServer.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Location", target);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    private String httpUrl(String path) {
        return "http://127.0.0.1:%d%s".formatted(httpServer.getAddress().getPort(), path);
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

    @ParameterizedTest
    @ValueSource(strings = {"/stream.ts", "/redirect.ts", "/master.m3u8", "/variant/media.m3u8"})
    void testPlayableHttpStreams(String path) {
        assertThat(filter.isUrlReachable(httpUrl(path), TIMEOUT)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/empty.ts", "/error.html", "/forbidden.ts", "/unknown.ts", "/loop.ts",
            "/broken.m3u8", "/nostreams.m3u8"})
    void testUnplayableHttpStreams(String path) {
        assertThat(filter.isUrlReachable(httpUrl(path), TIMEOUT)).isFalse();
    }

    @Test
    void testUnreachableHost() {
        assertThat(filter.isUrlReachable(closedUrl("stream.ts"), TIMEOUT)).isFalse();
    }

    @Test
    void testHostAcceptingConnectionsWithoutAnsweringIsNotReachable() {
        String url = "http://127.0.0.1:%d/stream.ts".formatted(silentServer.getLocalPort());
        assertThat(filter.isUrlReachable(url, 300)).isFalse();
    }

    @Test
    void testUserAgentOfVlcOptionsIsUsed() {
        assertThat(filter.isUrlReachable(httpUrl("/stream.ts"), TIMEOUT)).isTrue();
        assertThat(userAgents.get("/stream.ts")).isEqualTo(StreamProbe.DEFAULT_USER_AGENT);

        assertThat(filter.isUrlReachable(httpUrl("/stream.ts"), Map.of("http-user-agent", "MyPlayer/1.0"), TIMEOUT))
                .isTrue();
        assertThat(userAgents.get("/stream.ts")).isEqualTo("MyPlayer/1.0");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'RTSP/1.0 200 OK', true",
            "'RTSP/1.0 404 Not Found', false",
            "'RTSP/1.0 503 Service Unavailable', false",
            "'HTTP/1.1 200 OK', false"
    })
    void testRtspStreams(String statusLine, boolean expected) throws Exception {
        try (ServerSocket rtspServer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread server = Thread.ofVirtual().start(() -> {
                try (Socket client = rtspServer.accept()) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    String requestLine = reader.readLine();
                    String line;
                    do {
                        line = reader.readLine();
                    } while (line != null && !line.isEmpty());
                    String response = requestLine != null && requestLine.startsWith("DESCRIBE ")
                            ? statusLine + "\r\nCSeq: 1\r\n\r\n"
                            : "RTSP/1.0 400 Bad Request\r\n\r\n";
                    client.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                } catch (IOException ignored) {
                    // test failure shows up as unexpected result
                }
            });
            String url = "rtsp://127.0.0.1:%d/?avm=1&freq=330".formatted(rtspServer.getLocalPort());

            assertThat(filter.isUrlReachable(url, TIMEOUT)).isEqualTo(expected);
            server.join();
        }
    }

    @Test
    void testParseRtspStatus() {
        assertThat(StreamProbe.parseRtspStatus("RTSP/1.0 200 OK")).isEqualTo(200);
        assertThat(StreamProbe.parseRtspStatus("RTSP/1.0 454")).isEqualTo(454);
        assertThat(StreamProbe.parseRtspStatus("RTSP/1.0")).isEqualTo(-1);
        assertThat(StreamProbe.parseRtspStatus("garbage")).isEqualTo(-1);
        assertThat(StreamProbe.parseRtspStatus(null)).isEqualTo(-1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "http://bad url with blanks", "not-a-url", "unknown://127.0.0.1/stream"})
    void testInvalidUrlsAreNotReachableAndDoNotThrow(String url) {
        assertThat(filter.isUrlReachable(url, TIMEOUT)).isFalse();
    }

    @Test
    void testFileUrls(@TempDir Path tempDir) throws IOException {
        Path existing = Files.createFile(tempDir.resolve("stream.ts"));
        Path missing = tempDir.resolve("missing.ts");

        assertThat(filter.isUrlReachable(existing.toUri().toString(), 100)).isTrue();
        assertThat(filter.isUrlReachable(missing.toUri().toString(), 100)).isFalse();
    }

    @Test
    void testFilterKeepsOrderAndRemovesUnreachableEntries() throws IOException {
        Files.writeString(configFile, "timeoutMillis: %d\n".formatted(TIMEOUT));
        List<Entry> entries = List.of(
                entry("a", httpUrl("/stream.ts")),
                entry("b", closedUrl("b")),
                entry("c", "http://bad url"),
                entry("d", httpUrl("/master.m3u8")),
                entry("e", null),
                entry("f", httpUrl("/forbidden.ts")),
                entry("g", httpUrl("/redirect.ts")));

        assertThat(filter.filter(entries)).extracting(Entry::getName).containsExactly("a", "d", "g");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "http://www.google.de, 3000, true",
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
    void testFiltersAreComparedByIdentity() {
        assertThat(filter).isEqualTo(filter).isNotEqualTo(new ReachableUrlFilter(null));
        assertThat(filter).hasToString("ReachableUrlFilter[Reachable URL Filter]");
    }
}

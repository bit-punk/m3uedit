package org.bitpunk.m3uedit.plugins.enricher.epg;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EpgSourceCacheTest {

    /**
     * A clock the test can move forward.
     */
    private static class TestClock extends Clock {
        private Instant now = Instant.now();

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TempDir
    Path tempDir;
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger notModified = new AtomicInteger();
    private final AtomicReference<String> content = new AtomicReference<>("<tv/>");
    private final AtomicInteger status = new AtomicInteger(200);
    private TestClock clock;
    private EpgSourceCache cache;
    private String url;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/guide.xml", exchange -> {
            requests.incrementAndGet();
            String etag = "\"" + content.get().hashCode() + "\"";
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                notModified.incrementAndGet();
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            byte[] body = content.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("ETag", etag);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/guide.xml";
        clock = new TestClock();
        cache = new EpgSourceCache(XmltvTestFiles.CONTEXT, tempDir.resolve("cache"), HttpClient.newHttpClient(), clock);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void testSourceIsDownloadedOnceWithinTheMaximumAge() throws IOException {
        EpgSourceCache.CachedSource first = cache.get(url, Duration.ofHours(12));
        EpgSourceCache.CachedSource second = cache.get(url, Duration.ofHours(12));

        assertThat(first.warning()).isNull();
        assertThat(first.file()).hasContent("<tv/>");
        assertThat(second.file()).isEqualTo(first.file());
        assertThat(requests).hasValue(1);
    }

    @Test
    void testOutdatedSourceIsDownloadedAgainConditionally() throws IOException {
        cache.get(url, Duration.ofHours(12));
        clock.advance(Duration.ofHours(13));

        EpgSourceCache.CachedSource unchanged = cache.get(url, Duration.ofHours(12));
        assertThat(notModified).hasValue(1);
        assertThat(unchanged.file()).hasContent("<tv/>");
        // the age starts again after the check
        cache.get(url, Duration.ofHours(12));
        assertThat(requests).hasValue(2);

        content.set("<tv><channel id=\"a\"/></tv>");
        clock.advance(Duration.ofHours(13));
        assertThat(cache.get(url, Duration.ofHours(12)).file()).hasContent("<tv><channel id=\"a\"/></tv>");
    }

    @Test
    void testFailingUpdateUsesTheCachedFileAndWaitsBeforeTheNextAttempt() throws IOException {
        cache.get(url, Duration.ofHours(1));
        clock.advance(Duration.ofHours(2));
        content.set("changed");
        status.set(500);

        EpgSourceCache.CachedSource stale = cache.get(url, Duration.ofHours(1));
        assertThat(stale.file()).hasContent("<tv/>");
        assertThat(stale.warning()).contains("could not be updated").contains("HTTP status 500");

        EpgSourceCache.CachedSource again = cache.get(url, Duration.ofHours(1));
        assertThat(again.file()).hasContent("<tv/>");
        assertThat(again.warning()).as("reported once").isNull();
        assertThat(requests).hasValue(2);

        clock.advance(EpgSourceCache.RETRY_DELAY.plusMinutes(1));
        status.set(200);
        assertThat(cache.get(url, Duration.ofHours(1)).file()).hasContent("changed");
    }

    @Test
    void testSourceNeverDownloadedIsReported() {
        status.set(404);

        EpgSourceCache.CachedSource source = cache.get(url, Duration.ofHours(1));

        assertThat(source.file()).isNull();
        assertThat(source.warning()).contains("could not be downloaded").contains("HTTP status 404");
        assertThat(Files.exists(cache.cacheFileOf(url))).isFalse();
    }

    @Test
    void testLocalFilesAreUsedDirectly() throws IOException {
        Path local = XmltvTestFiles.write(tempDir.resolve("local.xml"), "<tv/>");

        assertThat(cache.get(local.toUri().toString(), Duration.ofHours(1)).file()).isEqualTo(local);
        assertThat(cache.get(tempDir.resolve("missing.xml").toUri().toString(), Duration.ofHours(1)).warning())
                .contains("cannot be read");
        assertThat(cache.get("ftp://example.com/guide.xml", Duration.ofHours(1)).warning()).contains("unsupported");
    }
}

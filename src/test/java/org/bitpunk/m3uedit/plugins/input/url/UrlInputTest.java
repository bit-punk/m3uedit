package org.bitpunk.m3uedit.plugins.input.url;

import com.sun.net.httpserver.HttpServer;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UrlInputTest {

    private static final String PLAYLIST = """
            #EXTM3U
            #EXTINF:-1,First, with comma
            http://example.com/first.ts
            #EXTINF:-1,Second
            http://example.com/second.ts
            """;

    @Test
    void testReadFromHttpUrl() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/playlist.m3u", exchange -> {
            byte[] body = PLAYLIST.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            UrlInput input = new UrlInput();
            input.setSelectedInputUrls(List.of(
                    URI.create("http://127.0.0.1:%d/playlist.m3u".formatted(server.getAddress().getPort())).toURL()));

            assertThat(input.read()).extracting(Entry::getName).containsExactly("First, with comma", "Second");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testReadFromFileUrlAndSkipUnreadableUrls(@TempDir Path tempDir) throws IOException {
        Path playlist = Files.writeString(tempDir.resolve("playlist.m3u"), PLAYLIST);
        UrlInput input = new UrlInput();
        input.setSelectedInputUrls(List.of(
                tempDir.resolve("missing.m3u").toUri().toURL(),
                playlist.toUri().toURL()));

        assertThat(input.read()).extracting(Entry::getName).containsExactly("First, with comma", "Second");
        // the stream has been closed, so the file can be deleted right away (fails on Windows otherwise)
        Files.delete(playlist);
    }
}

package org.bitpunk.m3uedit.plugins.input.url;

import com.sun.net.httpserver.HttpServer;
import org.bitpunk.m3uedit.api.InputReadException;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
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
import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
            UrlInput input = TestPluginSupport.create(UrlInput::new);
            input.setSelectedInputUrls(List.of(
                    URI.create("http://127.0.0.1:%d/playlist.m3u".formatted(server.getAddress().getPort())).toURL()));

            assertThat(input.read().entries()).extracting(Entry::getName).containsExactly("First, with comma", "Second");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testAnUnreadableUrlIsReportedTheOthersAreKept(@TempDir Path tempDir) throws IOException {
        Path playlist = Files.writeString(tempDir.resolve("playlist.m3u"), PLAYLIST);
        UrlInput input = TestPluginSupport.create(UrlInput::new);
        input.setSelectedInputUrls(List.of(
                tempDir.resolve("missing.m3u").toUri().toURL(),
                playlist.toUri().toURL()));

        InputReadException error = catchThrowableOfType(InputReadException.class, input::read);

        assertThat(error).hasMessageContaining("missing.m3u");
        assertThat(error.getEntriesRead()).extracting(Entry::getName).containsExactly("First, with comma", "Second");
        // the stream has been closed, so the file can be deleted right away (fails on Windows otherwise)
        Files.delete(playlist);
    }

    @Test
    void testACancelledDialogKeepsTheUrls() throws Exception {
        UrlInput input = TestPluginSupport.create(UrlInput::new);
        URL url = URI.create("http://example.com/tv.m3u").toURL();
        input.applyDialogResult(Optional.of(List.of(url)));

        input.applyDialogResult(Optional.empty());

        assertThat(input.getSelectedInputUrls()).containsExactly(url);
        assertThat(input.getSource()).contains("http://example.com/tv.m3u");
    }

    @Test
    void testTheDialogShowsTheCurrentUrls() throws Exception {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
        List<URL> urls = List.of(URI.create("http://example.com/a.m3u").toURL(),
                URI.create("http://example.com/b.m3u").toURL());
        java.util.concurrent.CompletableFuture<List<URL>> shown = new java.util.concurrent.CompletableFuture<>();
        javafx.application.Platform.runLater(() -> {
            try {
                UrlInputDialogController dialog = new UrlInputDialogController(new DefaultPluginContext(UrlInput.class));
                dialog.setUrls(urls);
                shown.complete(List.copyOf(dialog.selectedUrlListView.getItems()));
            } catch (Throwable t) {
                shown.completeExceptionally(t);
            }
        });

        assertThat(shown.get(10, java.util.concurrent.TimeUnit.SECONDS)).containsExactlyElementsOf(urls);
    }

    @Test
    void testTheSettingsAreRestored() throws Exception {
        UrlInput input = TestPluginSupport.create(UrlInput::new);
        URL url = URI.create("http://example.com/tv.m3u").toURL();
        input.applyDialogResult(Optional.of(List.of(url)));

        UrlInput restored = TestPluginSupport.create(UrlInput::new);
        restored.setSettings(input.getSettings());

        assertThat(restored.getSettingsType()).isEqualTo(UrlInputSettings.class);
        assertThat(input.getSettings()).isEqualTo(new UrlInputSettings(List.of("http://example.com/tv.m3u"),
                List.of("http://example.com/tv.m3u")));
        assertThat(restored.getSelectedInputUrls()).containsExactly(url);
        assertThat(restored.getSource()).isEqualTo(input.getSource());
    }

    @Test
    void testMissingSettingsAreNotConfigured() {
        UrlInput input = TestPluginSupport.create(UrlInput::new);

        input.setSettings(new UrlInputSettings(null, null));

        assertThat(input.getSelectedInputUrls()).isNull();
        assertThat(input.getSource()).isNull();
        assertThat(input.read().entries()).isEmpty();
    }

    @Test
    void testTheSettingsAreStoredInTheProjectFileAsBefore(@TempDir Path tempDir) throws IOException {
        Path projectFile = Files.writeString(tempDir.resolve("url.m3uproj"), """
                ---
                name: url
                inputs:
                  - type: UrlInput
                    enabled: true
                    selectedInputUrls:
                      - http://example.com/a.m3u
                      - http://example.com/b.m3u
                    urlList:
                      - http://example.com/a.m3u
                      - http://example.com/b.m3u
                """);

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        UrlInput input = (UrlInput) loaded.getInputs().getFirst();
        assertThat(input.getSelectedInputUrls()).containsExactly(URI.create("http://example.com/a.m3u").toURL(),
                URI.create("http://example.com/b.m3u").toURL());
        assertThat(input.getSource()).isEqualTo("[http://example.com/a.m3u, http://example.com/b.m3u]");
        assertThat(ProjectPersisterUtil.persistedState(input)).isEqualTo("""
                ---
                selectedInputUrls:
                  - http://example.com/a.m3u
                  - http://example.com/b.m3u
                urlList:
                  - http://example.com/a.m3u
                  - http://example.com/b.m3u
                """);
    }
}

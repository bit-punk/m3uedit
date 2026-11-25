package org.bitpunk.m3uedit.plugins.input.fritzbox;

import com.sun.net.httpserver.HttpServer;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.InputReadException;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

class FritzBoxInputTest {

    @Test
    void testBasicProperties() {
        FritzBoxInput input = TestPluginSupport.create(FritzBoxInput::new);
        assertThat(input.getName()).isEqualTo("FritzBox Input");
        assertThat(input.getDescription()).isEqualTo("Downloads a playlist from a FritzBox.");
        assertThat(input.getSource()).isNull();
        assertThat(PluginStates.enabled(input).get()).isFalse();

        PluginStates.enabled(input).set(true);
        assertThat(PluginStates.enabled(input).get()).isTrue();
    }

    @Test
    void testGetSourceWhenConfigured() {
        FritzBoxInput input = TestPluginSupport.create(FritzBoxInput::new);
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl("http://fritz.box/dvb/m3u/");
        connection.setSelectedStreamSources(List.of(new StreamSource("tvsd.m3u", "SD streams")));

        input.setFritzBoxConnection(connection);
        assertThat(input.getSource()).isEqualTo("base-url: http://fritz.box/dvb/m3u/, streams: tvsd.m3u");
    }

    @Test
    void testReadNullOrEmptyStreamSourcesReturnsEmptyList() throws Exception {
        FritzBoxInput input = TestPluginSupport.create(FritzBoxInput::new);
        input.setSelectedStreamSources(null);
        List<Entry> entriesNull = input.read().entries();
        assertThat(entriesNull).isNotNull().isEmpty();

        input.setSelectedStreamSources(Collections.emptyList());
        List<Entry> entriesEmpty = input.read().entries();
        assertThat(entriesEmpty).isNotNull().isEmpty();
    }

    @Test
    void testInputsAreComparedByIdentity() {
        FritzBoxInput input1 = TestPluginSupport.create(FritzBoxInput::new);
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl("http://fritz.box/dvb/m3u/");
        input1.setFritzBoxConnection(connection);
        FritzBoxInput input2 = TestPluginSupport.create(FritzBoxInput::new);
        input2.setFritzBoxConnection(connection);
        int hashCode = input1.hashCode();

        assertThat(input1).isEqualTo(input1).isNotEqualTo(input2).isNotEqualTo(null);
        input1.setSelectedStreamSources(List.of(new StreamSource("tvhd.m3u", "HD streams")));
        assertThat(input1.hashCode()).isEqualTo(hashCode);
    }

    private static final String PLAYLIST = """
            #EXTM3U
            #EXTINF:0,Das Erste HD
            #EXTVLCOPT:network-caching=1000
            rtsp://192.168.178.1:554/?avm=1&freq=330
            #EXTINF:0,ZDF HD
            rtsp://192.168.178.1:554/?avm=1&freq=450
            """;

    private static HttpServer startServer(Map<String, String> contents, List<String> requestedPaths) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            String content = contents.get(path);
            byte[] body = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(content == null ? 404 : 200, content == null ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    private static FritzBoxInput createInput(String baseUrl, StreamSource... streamSources) {
        FritzBoxInput input = TestPluginSupport.create(FritzBoxInput::new);
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl(baseUrl);
        connection.setSelectedStreamSources(List.of(streamSources));
        input.setFritzBoxConnection(connection);
        input.setSelectedStreamSources(List.of(streamSources));
        return input;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dvb/m3u/", "/dvb/m3u"})
    void testReadUsesConfiguredBaseUrl(String basePath) throws IOException {
        List<String> requestedPaths = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(Map.of("/dvb/m3u/tvhd.m3u", PLAYLIST), requestedPaths);
        try {
            String baseUrl = "http://127.0.0.1:%d%s".formatted(server.getAddress().getPort(), basePath);
            FritzBoxInput input = createInput(baseUrl, new StreamSource("tvhd.m3u", "HD streams"));

            List<Entry> entries = input.read().entries();

            assertThat(requestedPaths).containsExactly("/dvb/m3u/tvhd.m3u");
            assertThat(entries).extracting(Entry::getName).containsExactly("Das Erste HD", "ZDF HD");
            assertThat(entries.getFirst().getVlcOpts()).containsEntry("network-caching", "1000");
        } finally {
            server.stop(0);
        }
    }

    /**
     * Records the connections read successfully in the given directory.
     */
    private static FritzBoxInput createInput(Path recentDir, String baseUrl, StreamSource... streamSources) {
        FritzBoxInput input = TestPluginSupport.create(() -> new FritzBoxInput() {
            @Override
            protected RecentFritzBoxConnections createRecentConnections() {
                return new RecentFritzBoxConnections(recentDir.resolve(RecentFritzBoxConnections.CONFIG_FILE_NAME));
            }
        });
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl(baseUrl);
        connection.setSelectedStreamSources(List.of(streamSources));
        input.setFritzBoxConnection(connection);
        input.setSelectedStreamSources(List.of(streamSources));
        return input;
    }

    @Test
    void testAConnectionReadSuccessfullyIsOfferedAsRecentlyUsed(@TempDir Path dir) throws IOException {
        HttpServer server = startServer(Map.of("/dvb/m3u/tvhd.m3u", PLAYLIST), new CopyOnWriteArrayList<>());
        try {
            String baseUrl = "http://127.0.0.1:%d/dvb/m3u/".formatted(server.getAddress().getPort());
            createInput(dir, baseUrl, new StreamSource("tvhd.m3u", "HD streams")).read().entries();

            List<FritzBoxConnection> recent = new RecentFritzBoxConnections(
                    dir.resolve(RecentFritzBoxConnections.CONFIG_FILE_NAME)).getConnections();
            assertThat(recent).singleElement().extracting(FritzBoxConnection::getBaseUrl).isEqualTo(baseUrl);
            assertThat(recent.getFirst().getSelectedStreamSources()).extracting(StreamSource::getName)
                    .containsExactly("tvhd.m3u");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testAConnectionWithAnErrorIsNotOfferedAsRecentlyUsed(@TempDir Path dir) throws IOException {
        HttpServer server = startServer(Map.of("/dvb/m3u/tvhd.m3u", PLAYLIST), new CopyOnWriteArrayList<>());
        try {
            String baseUrl = "http://127.0.0.1:%d/dvb/m3u/".formatted(server.getAddress().getPort());
            // tvsd.m3u is not served
            FritzBoxInput input = createInput(dir, baseUrl, new StreamSource("tvsd.m3u", "SD streams"),
                    new StreamSource("tvhd.m3u", "HD streams"));
            assertThatThrownBy(input::read).isInstanceOf(InputReadException.class);

            assertThat(new RecentFritzBoxConnections(dir.resolve(RecentFritzBoxConnections.CONFIG_FILE_NAME))
                    .getConnections()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testAStreamSourceWithErrorStatusIsReportedTheOthersAreKept() throws IOException {
        List<String> requestedPaths = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(Map.of("/dvb/m3u/tvhd.m3u", PLAYLIST), requestedPaths);
        try {
            String baseUrl = "http://127.0.0.1:%d/dvb/m3u/".formatted(server.getAddress().getPort());
            FritzBoxInput input = createInput(baseUrl,
                    new StreamSource("tvsd.m3u", "SD streams"),     // not served -> 404
                    new StreamSource("tvhd.m3u", "HD streams"));

            InputReadException error = catchThrowableOfType(InputReadException.class, input::read);

            assertThat(requestedPaths).containsExactly("/dvb/m3u/tvsd.m3u", "/dvb/m3u/tvhd.m3u");
            assertThat(error).hasMessageStartingWith("tvsd.m3u: ").hasMessageContaining("404");
            // the HD streams are kept
            assertThat(error.getEntriesRead()).extracting(Entry::getName).containsExactly("Das Erste HD", "ZDF HD");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testResolveStreamSourceUriFallsBackToDefaultBaseUrl() {
        StreamSource sd = new StreamSource("tvsd.m3u", "SD streams");
        FritzBoxInput input = TestPluginSupport.create(FritzBoxInput::new);
        assertThat(input.resolveStreamSourceUri(sd)).hasToString(FritzBoxInput.DEFAULT_BASE_URL + "tvsd.m3u");

        input.setFritzBoxConnection(new FritzBoxConnection());   // connection without baseUrl
        assertThat(input.resolveStreamSourceUri(sd)).hasToString(FritzBoxInput.DEFAULT_BASE_URL + "tvsd.m3u");
    }

    @Test
    void testResolveStreamSourceUriUsesConfiguredHost() {
        FritzBoxInput input = createInput("  http://192.168.178.1/dvb/m3u  ");
        assertThat(input.resolveStreamSourceUri(new StreamSource("tvhd.m3u", "HD streams")))
                .hasToString("http://192.168.178.1/dvb/m3u/tvhd.m3u");
    }

    @Test
    void testACancelledDialogKeepsTheConfiguration() {
        FritzBoxInput input = createInput("http://192.168.178.1/dvb/m3u/", new StreamSource("tvhd.m3u", "HD streams"));

        input.applyDialogResult(java.util.Optional.empty());

        assertThat(input.getFritzBoxConnection().getBaseUrl()).isEqualTo("http://192.168.178.1/dvb/m3u/");
        assertThat(input.getSelectedStreamSources()).extracting(StreamSource::getName).containsExactly("tvhd.m3u");
    }

    @Test
    void testTheSettingsAreRestored() {
        FritzBoxInput input = createInput("http://192.168.178.1/dvb/m3u/", new StreamSource("tvhd.m3u", "HD streams"));

        FritzBoxInput restored = TestPluginSupport.create(FritzBoxInput::new);
        restored.setSettings(input.getSettings());

        assertThat(restored.getSettingsType()).isEqualTo(FritzBoxInputSettings.class);
        assertThat(restored.getSettings()).isEqualTo(input.getSettings());
        assertThat(restored.getFritzBoxConnection().getBaseUrl()).isEqualTo("http://192.168.178.1/dvb/m3u/");
        assertThat(restored.getSelectedStreamSources()).containsExactly(new StreamSource("tvhd.m3u", "HD streams"));
        assertThat(restored.getSource()).isEqualTo(input.getSource());
    }

    @Test
    void testMissingSettingsUseTheDefaultBaseUrl() throws IOException {
        FritzBoxInput input = createInput("http://192.168.178.1/dvb/m3u/", new StreamSource("tvhd.m3u", "HD streams"));

        input.setSettings(new FritzBoxInputSettings(null, null));

        assertThat(input.getSource()).isNull();
        assertThat(input.read().entries()).isEmpty();
        assertThat(input.resolveStreamSourceUri(new StreamSource("tvsd.m3u", "SD streams")))
                .hasToString(FritzBoxInput.DEFAULT_BASE_URL + "tvsd.m3u");
    }

    @Test
    void testTheSettingsAreStoredInTheProjectFileAsBefore() {
        DataModel loaded = ProjectPersisterUtil.loadProject(Path.of("src/test/resources/project/testproject.m3uproj"));

        FritzBoxInput input = (FritzBoxInput) loaded.getInputs().get(1);
        assertThat(input.getSelectedStreamSources()).containsExactly(new StreamSource("tvsd.m3u", "SD streams"),
                new StreamSource("tvhd.m3u", "HD streams"));
        assertThat(input.getFritzBoxConnection().getBaseUrl()).isEqualTo("http://fritz.box/dvb/m3u/");
        assertThat(input.getFritzBoxConnection().getSelectedStreamSources())
                .containsExactlyElementsOf(input.getSelectedStreamSources());
        assertThat(ProjectPersisterUtil.persistedState(input)).isEqualTo("""
                ---
                selectedStreamSources:
                  - name: tvsd.m3u
                    description: SD streams
                  - name: tvhd.m3u
                    description: HD streams
                fritzBoxConnection:
                  baseUrl: http://fritz.box/dvb/m3u/
                  selectedStreamSources:
                    - name: tvsd.m3u
                      description: SD streams
                    - name: tvhd.m3u
                      description: HD streams
                """);
    }
}

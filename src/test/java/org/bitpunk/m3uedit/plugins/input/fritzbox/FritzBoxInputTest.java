package org.bitpunk.m3uedit.plugins.input.fritzbox;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class FritzBoxInputTest {

    @Test
    void testBasicProperties() {
        FritzBoxInput input = new FritzBoxInput();
        assertThat(input.getName()).isEqualTo("FritzBox Input");
        assertThat(input.getDescription()).isEqualTo("Downloads a playlist from a FritzBox.");
        assertThat(input.getSource()).isNull();
        assertThat(input.getEnabled().get()).isFalse();

        input.getEnabled().set(true);
        assertThat(input.getEnabled().get()).isTrue();
    }

    @Test
    void testGetSourceWhenConfigured() {
        FritzBoxInput input = new FritzBoxInput();
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl("http://fritz.box/dvb/m3u/");
        connection.setSelectedStreamSources(List.of(new StreamSource("tvsd.m3u", "SD streams")));

        input.setFritzBoxConnection(connection);
        assertThat(input.getSource()).isEqualTo("base-url: http://fritz.box/dvb/m3u/, streams: tvsd.m3u");
    }

    @Test
    void testReadNullOrEmptyStreamSourcesReturnsEmptyList() {
        FritzBoxInput input = new FritzBoxInput();
        input.setSelectedStreamSources(null);
        List<Entry> entriesNull = input.read();
        assertThat(entriesNull).isNotNull().isEmpty();

        input.setSelectedStreamSources(Collections.emptyList());
        List<Entry> entriesEmpty = input.read();
        assertThat(entriesEmpty).isNotNull().isEmpty();
    }

    @Test
    void testInputsAreComparedByIdentity() {
        FritzBoxInput input1 = new FritzBoxInput();
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl("http://fritz.box/dvb/m3u/");
        input1.setFritzBoxConnection(connection);
        FritzBoxInput input2 = new FritzBoxInput();
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
        FritzBoxInput input = new FritzBoxInput();
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

            List<Entry> entries = input.read();

            assertThat(requestedPaths).containsExactly("/dvb/m3u/tvhd.m3u");
            assertThat(entries).extracting(Entry::getName).containsExactly("Das Erste HD", "ZDF HD");
            assertThat(entries.getFirst().getVlcOpts()).containsEntry("network-caching", "1000");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testReadSkipsStreamSourcesWithErrorStatus() throws IOException {
        List<String> requestedPaths = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(Map.of("/dvb/m3u/tvhd.m3u", PLAYLIST), requestedPaths);
        try {
            String baseUrl = "http://127.0.0.1:%d/dvb/m3u/".formatted(server.getAddress().getPort());
            FritzBoxInput input = createInput(baseUrl,
                    new StreamSource("tvsd.m3u", "SD streams"),     // not served -> 404
                    new StreamSource("tvhd.m3u", "HD streams"));

            List<Entry> entries = input.read();

            assertThat(requestedPaths).containsExactly("/dvb/m3u/tvsd.m3u", "/dvb/m3u/tvhd.m3u");
            assertThat(entries).extracting(Entry::getName).containsExactly("Das Erste HD", "ZDF HD");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testResolveStreamSourceUriFallsBackToDefaultBaseUrl() {
        StreamSource sd = new StreamSource("tvsd.m3u", "SD streams");
        FritzBoxInput input = new FritzBoxInput();
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
}

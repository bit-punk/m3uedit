package org.bitpunk.m3uedit.plugins.input.fritzbox;

import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.bitpunk.m3uedit.testsupport.ConfigDirectoryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentFritzBoxConnectionsTest {
    static final StreamSource SD = new StreamSource("tvsd.m3u", "SD streams");
    static final StreamSource HD = new StreamSource("tvhd.m3u", "HD streams");

    @TempDir
    Path dir;

    private RecentFritzBoxConnections recent() {
        return new RecentFritzBoxConnections(new ConfigDirectoryContext(FritzBoxInput.class, dir));
    }

    static FritzBoxConnection connection(String baseUrl, StreamSource... streams) {
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl(baseUrl);
        connection.setSelectedStreamSources(List.of(streams));
        return connection;
    }

    @Test
    void testTheMostRecentlyUsedConnectionIsFirstAndKeepsItsStreams() {
        recent().recordSuccess(connection("http://fritz.box/dvb/m3u/", SD, HD));
        recent().recordSuccess(connection("http://192.168.178.1/dvb/m3u/", HD));
        // the same FritzBox with other streams: moved to the top, the streams replaced
        recent().recordSuccess(connection("HTTP://fritz.box/dvb/m3u", HD, HD));

        List<FritzBoxConnection> connections = recent().getConnections();

        assertThat(connections).extracting(FritzBoxConnection::getBaseUrl)
                .containsExactly("HTTP://fritz.box/dvb/m3u", "http://192.168.178.1/dvb/m3u/");
        assertThat(connections.getFirst().getSelectedStreamSources()).containsExactly(HD);
    }

    @Test
    void testAtMostTenConnectionsAreKept() {
        for (int i = 1; i <= 12; i++) {
            recent().recordSuccess(connection("http://192.168.178.%d/dvb/m3u/".formatted(i), SD));
        }

        assertThat(recent().getConnections()).hasSize(RecentFritzBoxConnections.MAX_ENTRIES)
                .first().extracting(FritzBoxConnection::getBaseUrl).isEqualTo("http://192.168.178.12/dvb/m3u/");
    }

    @Test
    void testAConnectionIsRemoved() {
        recent().recordSuccess(connection("http://fritz.box/dvb/m3u/", SD));
        recent().recordSuccess(connection("http://192.168.178.1/dvb/m3u/", HD));

        recent().remove(connection("http://fritz.box/dvb/m3u/"));

        assertThat(recent().getConnections()).extracting(FritzBoxConnection::getBaseUrl)
                .containsExactly("http://192.168.178.1/dvb/m3u/");
    }

    @Test
    void testConnectionsWithoutUrlAreIgnored() {
        recent().recordSuccess(connection(" "));
        recent().recordSuccess(null);

        assertThat(recent().getConnections()).isEmpty();
    }
}

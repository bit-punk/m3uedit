package org.bitpunk.m3uedit.plugins.input.iptvorg;

import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IptvOrgApiClientTest {
    private static final PluginContext CONTEXT = new DefaultPluginContext(IptvOrgInput.class);
    private static final String COUNTRIES_JSON = """
            [{"name":"Germany","code":"DE","languages":["deu"],"flag":"🇩🇪"},
             {"name":"Austria","code":"AT","languages":["deu"],"flag":"🇦🇹"},
             {"name":"no code"}]
            """;
    private static final String CATEGORIES_JSON = """
            [{"id":"news","name":"News","description":"Programming is mostly news"},
             {"id":"auto","name":"Auto","description":"Programming related to cars"}]
            """;
    private static final String ALL_STREAMS_M3U = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ZDF.de@SD" group-title="General",ZDF (720p) [Geo-blocked]
            https://example.com/zdf1.m3u8
            #EXTINF:-1 tvg-id="3sat.de@SD" group-title="General",3sat
            https://example.com/3sat.m3u8
            #EXTINF:-1 tvg-id="ZDF.de@HD" group-title="General",ZDF HD (1080p)
            https://example.com/zdf2.m3u8
            """;

    @Test
    void testParseCountriesSortedByNameAndSkipsEntriesWithoutCode() {
        List<IptvOrgPlaylist> playlists = new IptvOrgApiClient(CONTEXT).parsePlaylists(PlaylistGrouping.COUNTRY, toStream(COUNTRIES_JSON));

        assertThat(playlists).containsExactly(
                new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "AT", "Austria"),
                new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany"));
    }

    @Test
    void testParseCategoriesUsesIdField() {
        List<IptvOrgPlaylist> playlists = new IptvOrgApiClient(CONTEXT).parsePlaylists(PlaylistGrouping.CATEGORY, toStream(CATEGORIES_JSON));

        assertThat(playlists)
                .extracting(IptvOrgPlaylist::getId)
                .containsExactly("auto", "news");
    }

    @Test
    void testParseChannelsReturnsOneEntryPerChannelSortedByName() throws IOException {
        List<IptvOrgPlaylist> channels = new IptvOrgApiClient(CONTEXT).parseChannels(toStream(ALL_STREAMS_M3U));

        assertThat(channels).containsExactly(
                new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "3sat.de", "3sat"),
                new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "ZDF.de", "ZDF"));
    }

    @Test
    void testGetChannelsReadsFromPlaylistContainingAllStreams() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer().serve("/index.m3u", ALL_STREAMS_M3U)) {
            IptvOrgApiClient client = new IptvOrgApiClient(CONTEXT, server.getBaseUrl(), server.getBaseUrl());

            assertThat(client.getPlaylists(PlaylistGrouping.CHANNEL))
                    .extracting(IptvOrgPlaylist::getName)
                    .containsExactly("3sat", "ZDF");
        }
    }

    @Test
    void testGetPlaylistsReadsFromApiAndCachesResult() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer().serve("/countries.json", COUNTRIES_JSON)) {
            IptvOrgApiClient client = new IptvOrgApiClient(CONTEXT, server.getBaseUrl(), server.getBaseUrl());

            assertThat(client.getPlaylists(PlaylistGrouping.COUNTRY)).hasSize(2);
            assertThat(client.getPlaylists(PlaylistGrouping.COUNTRY)).hasSize(2);
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void testGetPlaylistsThrowsIOExceptionOnHttpError() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer()) {
            IptvOrgApiClient client = new IptvOrgApiClient(CONTEXT, server.getBaseUrl(), server.getBaseUrl());

            assertThatThrownBy(() -> client.getPlaylists(PlaylistGrouping.REGION))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("404");
        }
    }

    private static ByteArrayInputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}

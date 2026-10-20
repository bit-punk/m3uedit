package org.bitpunk.m3uedit.plugins.input.iptvorg;

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
    private static final String COUNTRIES_JSON = """
            [{"name":"Germany","code":"DE","languages":["deu"],"flag":"🇩🇪"},
             {"name":"Austria","code":"AT","languages":["deu"],"flag":"🇦🇹"},
             {"name":"no code"}]
            """;
    private static final String CATEGORIES_JSON = """
            [{"id":"news","name":"News","description":"Programming is mostly news"},
             {"id":"auto","name":"Auto","description":"Programming related to cars"}]
            """;

    @Test
    void testParseCountriesSortedByNameAndSkipsEntriesWithoutCode() {
        List<IptvOrgPlaylist> playlists = new IptvOrgApiClient().parsePlaylists(PlaylistGrouping.COUNTRY, toStream(COUNTRIES_JSON));

        assertThat(playlists).containsExactly(
                new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "AT", "Austria"),
                new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany"));
    }

    @Test
    void testParseCategoriesUsesIdField() {
        List<IptvOrgPlaylist> playlists = new IptvOrgApiClient().parsePlaylists(PlaylistGrouping.CATEGORY, toStream(CATEGORIES_JSON));

        assertThat(playlists)
                .extracting(IptvOrgPlaylist::getId)
                .containsExactly("auto", "news");
    }

    @Test
    void testGetPlaylistsReadsFromApiAndCachesResult() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer().serve("/countries.json", COUNTRIES_JSON)) {
            IptvOrgApiClient client = new IptvOrgApiClient(server.getBaseUrl());

            assertThat(client.getPlaylists(PlaylistGrouping.COUNTRY)).hasSize(2);
            assertThat(client.getPlaylists(PlaylistGrouping.COUNTRY)).hasSize(2);
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void testGetPlaylistsThrowsIOExceptionOnHttpError() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer()) {
            IptvOrgApiClient client = new IptvOrgApiClient(server.getBaseUrl());

            assertThatThrownBy(() -> client.getPlaylists(PlaylistGrouping.REGION))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("404");
        }
    }

    private static ByteArrayInputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}

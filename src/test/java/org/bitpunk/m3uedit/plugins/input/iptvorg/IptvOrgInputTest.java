package org.bitpunk.m3uedit.plugins.input.iptvorg;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class IptvOrgInputTest {
    private static final IptvOrgPlaylist GERMANY = new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany");
    private static final IptvOrgPlaylist NEWS = new IptvOrgPlaylist(PlaylistGrouping.CATEGORY, "news", "News");
    private static final IptvOrgPlaylist UNKNOWN = new IptvOrgPlaylist(PlaylistGrouping.LANGUAGE, "xyz", "Unknown");
    private static final IptvOrgPlaylist ZDF = new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "ZDF.de", "ZDF");
    private static final IptvOrgPlaylist KIKA = new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "KiKA.de", "KiKA");

    @Test
    void testBasicProperties() {
        IptvOrgInput input = new IptvOrgInput();
        assertThat(input.getName()).isEqualTo("IPTV-org Input");
        assertThat(input.getDescription()).contains("iptv-org");
        assertThat(input.isConfigurable()).isTrue();
        assertThat(input.getSelectedPlaylists()).isEmpty();
        assertThat(input.getSource()).isNull();
        assertThat(input.getEnabled().get()).isFalse();
    }

    @Test
    void testGetSourceListsPlaylistPaths() {
        IptvOrgInput input = new IptvOrgInput();
        input.setSelectedPlaylists(List.of(GERMANY, NEWS, ZDF));
        assertThat(input.getSource()).isEqualTo("countries/de.m3u, categories/news.m3u, channels/ZDF.de");
    }

    @Test
    void testReadOnlyStreamsOfSelectedChannels() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer()
                .serve("/index.m3u", """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="ZDF.de@SD" group-title="General",ZDF (720p)
                        https://example.com/zdf1.m3u8
                        #EXTINF:-1 tvg-id="3sat.de@SD" group-title="General",3sat
                        https://example.com/3sat.m3u8
                        #EXTINF:-1 tvg-id="KiKA.de@SD" group-title="Kids",KiKA
                        https://example.com/kika.m3u8
                        #EXTINF:-1 tvg-id="ZDF.de@HD" group-title="General",ZDF HD (1080p)
                        https://example.com/zdf2.m3u8
                        """)) {
            IptvOrgInput input = new IptvOrgInput();
            input.setPlaylistBaseUrl(server.getBaseUrl());
            input.setSelectedPlaylists(List.of(ZDF, KIKA));

            List<Entry> entries = input.read();

            assertThat(entries)
                    .extracting(Entry::getChannelUri)
                    .containsExactly("https://example.com/zdf1.m3u8", "https://example.com/kika.m3u8", "https://example.com/zdf2.m3u8");
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void testReadEntriesOfAllSelectedPlaylistsAndSkipsMissingOnes() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer()
                .serve("/countries/de.m3u", """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="3sat.de@SD" tvg-logo="https://example.com/3sat.png" group-title="General",3sat
                        https://example.com/3sat.m3u8
                        """)
                .serve("/categories/news.m3u", """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="2GB.au@SD" group-title="News",2GB (1080p)
                        https://example.com/2gb.m3u8
                        """)) {
            IptvOrgInput input = new IptvOrgInput();
            input.setPlaylistBaseUrl(server.getBaseUrl());
            input.setSelectedPlaylists(List.of(GERMANY, UNKNOWN, NEWS));

            List<Entry> entries = input.read();

            assertThat(entries)
                    .extracting(Entry::getName, Entry::getChannelUri, Entry::getGroupTitle)
                    .containsExactly(
                            tuple("3sat", "https://example.com/3sat.m3u8", "General"),
                            tuple("2GB (1080p)", "https://example.com/2gb.m3u8", "News"));
        }
    }

    @Test
    void testReadWithoutSelectionReturnsEmptyList() {
        IptvOrgInput input = new IptvOrgInput();
        input.setSelectedPlaylists(null);
        assertThat(input.read()).isEmpty();
    }

    @Test
    void testEqualsAndHashCodeIgnoreBaseUrl() {
        IptvOrgInput input1 = new IptvOrgInput();
        input1.setSelectedPlaylists(List.of(GERMANY));

        IptvOrgInput input2 = new IptvOrgInput();
        input2.setEnabled(input1.getEnabled());
        input2.setSelectedPlaylists(List.of(GERMANY));
        input2.setPlaylistBaseUrl("http://localhost/");

        IptvOrgInput input3 = new IptvOrgInput();
        input3.setEnabled(input1.getEnabled());
        input3.setSelectedPlaylists(List.of(NEWS));

        assertThat(input1)
                .isEqualTo(input2)
                .hasSameHashCodeAs(input2)
                .isNotEqualTo(input3);
    }
}

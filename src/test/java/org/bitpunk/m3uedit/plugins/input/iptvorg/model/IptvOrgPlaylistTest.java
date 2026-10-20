package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IptvOrgPlaylistTest {

    @Test
    void testGetPathUsesGroupingAndLowerCaseId() {
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany").getPath()).isEqualTo("countries/de.m3u");
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.CATEGORY, "news", "News").getPath()).isEqualTo("categories/news.m3u");
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.LANGUAGE, "deu", "German").getPath()).isEqualTo("languages/deu.m3u");
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.REGION, "EUR", "Europe").getPath()).isEqualTo("regions/eur.m3u");
    }

    @Test
    void testToString() {
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany")).hasToString("Country: Germany (DE)");
    }
}

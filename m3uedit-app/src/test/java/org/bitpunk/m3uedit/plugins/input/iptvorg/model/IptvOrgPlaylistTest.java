package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.input.iptvorg.IptvOrgInput;
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
    void testChannelIsReadFromPlaylistContainingAllStreams() {
        IptvOrgPlaylist channel = new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "3sat.de", "3sat");
        assertThat(channel.getPath()).isEqualTo("index.m3u");
        assertThat(channel.getSourceName()).isEqualTo("channels/3sat.de");
    }

    @Test
    void testSourceNameOfPlaylistIsItsPath() {
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany").getSourceName()).isEqualTo("countries/de.m3u");
    }

    @Test
    void testDescribeShowsTheLabelOfTheGrouping() {
        PluginContext context = new DefaultPluginContext(IptvOrgInput.class);
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany").describe(context))
                .isEqualTo("Country: Germany (DE)");
    }

    @Test
    void testToString() {
        assertThat(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany")).hasToString("COUNTRY: Germany (DE)");
    }
}

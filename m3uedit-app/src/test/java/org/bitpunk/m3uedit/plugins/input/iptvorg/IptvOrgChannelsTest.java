package org.bitpunk.m3uedit.plugins.input.iptvorg;

import org.bitpunk.m3uedit.api.Entry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IptvOrgChannelsTest {

    @Test
    void testChannelIdIsTvgIdWithoutFeed() {
        assertThat(IptvOrgChannels.channelIdOf(entry("3sat.de@SD", "3sat"))).isEqualTo("3sat.de");
        assertThat(IptvOrgChannels.channelIdOf(entry("3sat.de", "3sat"))).isEqualTo("3sat.de");
    }

    @Test
    void testChannelIdFallsBackToChannelNameWithoutTvgId() {
        assertThat(IptvOrgChannels.channelIdOf(entry(null, "TV Publica (1080p)"))).isEqualTo("TV Publica");
        assertThat(IptvOrgChannels.channelIdOf(entry("", "TV Publica"))).isEqualTo("TV Publica");
    }

    @Test
    void testChannelNameWithoutQualityAndLabels() {
        assertThat(IptvOrgChannels.channelNameOf(entry(null, "ZDF (720p) [Geo-blocked]"))).isEqualTo("ZDF");
        assertThat(IptvOrgChannels.channelNameOf(entry(null, "Arirang [Not 24/7]"))).isEqualTo("Arirang");
        assertThat(IptvOrgChannels.channelNameOf(entry(null, "1+1 International (576i)"))).isEqualTo("1+1 International");
        assertThat(IptvOrgChannels.channelNameOf(entry(null, "Channel (Region) 1"))).isEqualTo("Channel (Region) 1");
    }

    private static Entry entry(String tvgId, String name) {
        Entry entry = new Entry();
        entry.setTvgId(tvgId);
        entry.setName(name);
        return entry;
    }
}

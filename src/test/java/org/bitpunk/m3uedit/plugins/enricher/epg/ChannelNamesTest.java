package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "ZDF HD; zdf",
            "DE: ZDF; zdf",
            "DE | Das Erste HD; daserste",
            "[DE] Das Erste FHD; daserste",
            "das-erste; daserste",
            "ProSieben UHD 4K; prosieben",
            "Sky Sport 1 HD 1080p; skysport1",
            "Nick Jr.; nickjr",
            "Pro7 MAXX; pro7maxx",
            "Phönix; phoenix",
            "Télé 2; tele2",
            "Sport1+; sport1plus",
            "Sixx & more; sixxandmore",
            "HD; ''",
    })
    void testNamesAreNormalized(String name, String normalized) {
        assertThat(ChannelNames.normalize(name)).isEqualTo(normalized == null ? "" : normalized);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {"ZDF HD; ZDF", "Das Erste HD; Das Erste", "arte HD; ARTE"})
    void testPlaylistNamesMatchDisplayNames(String playlistName, String displayName) {
        assertThat(ChannelNames.normalize(playlistName)).isEqualTo(ChannelNames.normalize(displayName));
    }

    @org.junit.jupiter.api.Test
    void testPrefixesAreMadeOfTheFirstWords() {
        assertThat(ChannelNames.prefixes("WDR HD Köln")).containsExactly("wdr");
        assertThat(ChannelNames.prefixes("NDR FS NDS HD")).containsExactly("ndrfs", "ndr");
        assertThat(ChannelNames.prefixes("hr-fernsehen HD")).containsExactly("hr");
        assertThat(ChannelNames.prefixes("ZDF HD")).isEmpty();
        assertThat(ChannelNames.prefixes(null)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void testDisplayNamesOfSeveralChannelsAreSplit() {
        assertThat(ChannelNames.alternatives("SWR/SR")).containsExactly("SWR/SR", "SWR", "SR");
        assertThat(ChannelNames.alternatives("ZDF")).containsExactly("ZDF");
    }
}

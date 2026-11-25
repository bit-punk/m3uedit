package org.bitpunk.m3uedit.util;

import org.bitpunk.m3uedit.api.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistClipboardTest {

    private static Entry entry() {
        Entry entry = new Entry();
        entry.setName("Das Erste HD");
        entry.setDuration("-1");
        entry.setTvgId("DasErste.de");
        entry.setTvgName("Das Erste HD");
        entry.setTvgLogo("https://example.com/ard.png");
        entry.setGroupTitle("Public");
        entry.setChannelUri("rtsp://192.168.178.1:554/?avm=1&freq=330");
        entry.setVlcOpts(Map.of("network-caching", "1000"));
        return entry;
    }

    @Test
    void testCopiedEntriesArePastedWithAllTheirAttributes() {
        String text = PlaylistClipboard.toM3u(List.of(entry()));

        List<Entry> pasted = PlaylistClipboard.fromM3u(text);

        assertThat(text).startsWith("#EXTM3U").contains("#EXTINF", "#EXTVLCOPT:network-caching=1000");
        assertThat(pasted).singleElement().satisfies(entry -> {
            assertThat(entry.getName()).isEqualTo("Das Erste HD");
            assertThat(entry.getTvgId()).isEqualTo("DasErste.de");
            assertThat(entry.getTvgLogo()).isEqualTo("https://example.com/ard.png");
            assertThat(entry.getGroupTitle()).isEqualTo("Public");
            assertThat(entry.getChannelUri()).isEqualTo("rtsp://192.168.178.1:554/?avm=1&freq=330");
            assertThat(entry.getVlcOpts()).containsEntry("network-caching", "1000");
        });
    }

    @Test
    void testTextWithoutHeaderIsPastedToo() {
        String lines = """
                #EXTINF:-1 tvg-id="arte.de" group-title="Culture",arte HD
                http://example.com/arte
                """;

        assertThat(PlaylistClipboard.fromM3u(lines)).extracting(Entry::getName).containsExactly("arte HD");
    }

    @Test
    void testTextWithoutEntriesPastesNothing() {
        assertThat(PlaylistClipboard.fromM3u(null)).isEmpty();
        assertThat(PlaylistClipboard.fromM3u("just some text")).isEmpty();
        assertThat(PlaylistClipboard.fromM3u("#EXTM3U\nhttp://example.com/without-extinf")).isEmpty();
    }
}

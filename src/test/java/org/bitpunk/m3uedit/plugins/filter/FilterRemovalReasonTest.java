package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.channelname.ChannelNameFilter;
import org.bitpunk.m3uedit.plugins.filter.channelname.model.ChannelNameFilterConfig;
import org.bitpunk.m3uedit.plugins.filter.reachableurl.ReachableUrlFilter;
import org.bitpunk.m3uedit.plugins.filter.sdchannel.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.filter.uniqueuri.UniqueUriFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterRemovalReasonTest {

    private static Entry entry(String name, String uri) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri(uri);
        return entry;
    }

    @Test
    void sdChannelFilterNamesTheLongerChannel() {
        Entry sd = entry("ZDF", "http://a");
        Entry hd = entry("ZDF HD", "http://b");
        List<Entry> input = List.of(sd, hd);
        SdChannelFilter filter = new SdChannelFilter();

        assertThat(filter.filter(input)).containsExactly(hd);
        assertThat(filter.describeRemoval(sd, input)).isEqualTo("\"ZDF HD\" exists");
    }

    @Test
    void uniqueNameFilterNamesTheKeptEntry() {
        Entry first = entry("WELT", "http://a");
        Entry duplicate = entry("WELT", "http://b");
        List<Entry> input = List.of(entry("n-tv", "http://c"), first, duplicate);
        UniqueNameFilter filter = new UniqueNameFilter();

        assertThat(filter.filter(input)).doesNotContain(duplicate);
        assertThat(filter.describeRemoval(duplicate, input)).isEqualTo("same name as \"WELT\" (#2)");
    }

    @Test
    void uniqueUriFilterNamesTheKeptEntry() {
        Entry first = entry("Das Erste HD", "http://same");
        Entry duplicate = entry("ARD", "http://same");
        List<Entry> input = List.of(first, duplicate);
        UniqueUriFilter filter = new UniqueUriFilter();

        assertThat(filter.filter(input)).containsExactly(first);
        assertThat(filter.describeRemoval(duplicate, input)).isEqualTo("same URL as \"Das Erste HD\" (#1)");
    }

    @Test
    void uniqueFilterHasNoReasonForTheKeptEntry() {
        Entry first = entry("WELT", "http://a");
        List<Entry> input = List.of(first, entry("WELT", "http://b"));

        assertThat(new UniqueNameFilter().describeRemoval(first, input)).isNull();
    }

    @Test
    void channelNameFilterNamesTheMatchingExpression(@TempDir Path tempDir) {
        ChannelNameFilter filter = new ChannelNameFilter(tempDir.resolve("channel_name_filter.yaml"));
        ChannelNameFilterConfig config = new ChannelNameFilterConfig();
        config.setExcludeRegexes(List.of("^Bibel.*$", "(?i).*QVC.*"));
        filter.setConfig(config);
        Entry qvc = entry("QVC Style", "http://a");

        assertThat(filter.filter(List.of(qvc))).isEmpty();
        assertThat(filter.describeRemoval(qvc, List.of(qvc))).isEqualTo("matches (?i).*QVC.*");
    }

    @Test
    void reachableUrlFilterNamesTheUrl() {
        Entry entry = entry("TV Berlin", "https://tvberlin.example/live.m3u8");

        assertThat(new ReachableUrlFilter().describeRemoval(entry, List.of(entry)))
                .isEqualTo("no connection to https://tvberlin.example/live.m3u8");
    }
}

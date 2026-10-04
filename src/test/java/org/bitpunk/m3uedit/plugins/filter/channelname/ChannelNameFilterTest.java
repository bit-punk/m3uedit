package org.bitpunk.m3uedit.plugins.filter.channelname;

import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractFilterTest;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.channelname.model.ChannelNameFilterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelNameFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = new ChannelNameFilter();
        List<Entry> entryList = readList("iptv.m3u");
        assertThat(entryList).hasSize(33);
        List<Entry> filteredList = filter.filter(entryList);
        assertThat(filteredList).hasSize(24);
        assertThat(filteredList).filteredOn(channel -> channel.getName().matches(".*QVC.*")).isEmpty();
        assertThat(filteredList).filteredOn(channel -> channel.getName().matches(".*HSE.*")).isEmpty();
    }

    @Test
    void testCustomUserConfigPath(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("channel_name_filter.yaml");
        ChannelNameFilterConfig customConfig = ChannelNameFilterConfig.builder()
                .excludeRegexes(List.of(".*Das Erste.*"))
                .build();
        YamlConfigManager<ChannelNameFilterConfig> manager = new YamlConfigManager<>(configPath, ChannelNameFilterConfig.class);
        manager.saveConfig(customConfig);

        ChannelNameFilter filter = new ChannelNameFilter(configPath);
        Entry e1 = new Entry();
        e1.setName("Das Erste HD");
        Entry e2 = new Entry();
        e2.setName("ZDF HD");

        List<Entry> filtered = filter.filter(List.of(e1, e2));
        assertThat(filtered).extracting(Entry::getName).containsExactly("ZDF HD");
    }

    @Test
    void testNullAndEmptyList() {
        ChannelNameFilter filter = new ChannelNameFilter();
        assertThat(filter.filter(null)).isEmpty();
        assertThat(filter.filter(List.of())).isEmpty();
    }
}

package org.bitpunk.m3uedit.plugins.filter.channelname;

import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractFilterTest;
import org.bitpunk.m3uedit.testsupport.ConfigDirectoryContext;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.NoSettings;
import org.bitpunk.m3uedit.plugins.filter.channelname.model.ChannelNameFilterConfig;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelNameFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = TestPluginSupport.create(ChannelNameFilter::new);
        List<Entry> entryList = readList("iptv.m3u");
        assertThat(entryList).hasSize(33);
        List<Entry> filteredList = filter.filter(entryList);
        assertThat(filteredList).hasSize(24);
        assertThat(filteredList).filteredOn(channel -> channel.getName().matches(".*QVC.*")).isEmpty();
        assertThat(filteredList).filteredOn(channel -> channel.getName().matches(".*HSE.*")).isEmpty();
    }

    @Test
    void testCustomUserConfigPath(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve(ChannelNameFilter.CONFIG_FILE_NAME);
        ChannelNameFilterConfig customConfig = ChannelNameFilterConfig.builder()
                .excludeRegexes(List.of(".*Das Erste.*"))
                .build();
        YamlConfigManager<ChannelNameFilterConfig> manager = new YamlConfigManager<>(configPath, ChannelNameFilterConfig.class);
        manager.saveConfig(customConfig);

        ChannelNameFilter filter = ConfigDirectoryContext.create(ChannelNameFilter::new, tempDir);
        Entry e1 = new Entry();
        e1.setName("Das Erste HD");
        Entry e2 = new Entry();
        e2.setName("ZDF HD");

        List<Entry> filtered = filter.filter(List.of(e1, e2));
        assertThat(filtered).extracting(Entry::getName).containsExactly("ZDF HD");
    }

    @Test
    void testNoSettingsInProjectFile() {
        ChannelNameFilter filter = TestPluginSupport.create(ChannelNameFilter::new);
        assertThat(filter.getSettingsType()).isEqualTo(NoSettings.class);
        assertThat(filter.getSettings()).isEqualTo(NoSettings.INSTANCE);
    }

    @Test
    void testNullAndEmptyList() {
        ChannelNameFilter filter = TestPluginSupport.create(ChannelNameFilter::new);
        assertThat(filter.filter(null)).isEmpty();
        assertThat(filter.filter(List.of())).isEmpty();
    }
}

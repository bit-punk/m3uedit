package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SorterPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = SorterPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins).hasSize(1);
        assertThat(allPlugins).extracting("name").contains("ChannelName Sorter");
    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = SorterPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames).hasSize(1);
        assertThat(allPluginNames).contains("ChannelName Sorter");
    }
}
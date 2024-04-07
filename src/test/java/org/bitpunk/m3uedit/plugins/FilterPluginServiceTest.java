package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = FilterPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins).hasSize(5);
        assertThat(allPlugins).filteredOn(plugin -> "ChannelName Filter".equals(plugin.getName())).isNotEmpty();
        assertThat(allPlugins).filteredOn(plugin -> "Reachable URL Filter".equals(plugin.getName())).isNotEmpty();
        assertThat(allPlugins).filteredOn(plugin -> "SD Channel Filter".equals(plugin.getName())).isNotEmpty();
        assertThat(allPlugins).filteredOn(plugin -> "Unique Name Filter".equals(plugin.getName())).isNotEmpty();
        assertThat(allPlugins).filteredOn(plugin -> "Unique URI Filter".equals(plugin.getName())).isNotEmpty();
    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = FilterPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames).hasSize(5).contains(
                "ChannelName Filter",
                "Reachable URL Filter",
                "SD Channel Filter",
                "Unique Name Filter",
                "Unique URI Filter"
        );
    }
}
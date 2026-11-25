package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterPluginServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ChannelName Filter",
            "Reachable URL Filter",
            "SD Channel Filter",
            "Unique Name Filter",
            "Unique URI Filter"
    })
    void getAllPlugins(String pluginName) {
        List<? extends Plugin> allPlugins = FilterPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins).hasSize(5);
        assertThat(allPlugins).filteredOn(plugin -> pluginName.equals(plugin.getName())).isNotEmpty();
    }

    @Test
    void testTheNamesAreReadWithoutCreatingThePlugins() {
        List<String> allPluginNames = FilterPluginService.getInstance().getDescriptors().stream()
                .map(PluginDescriptor::name).toList();
        assertThat(allPluginNames).hasSize(5).contains(
                "ChannelName Filter",
                "Reachable URL Filter",
                "SD Channel Filter",
                "Unique Name Filter",
                "Unique URI Filter"
        );
    }
}
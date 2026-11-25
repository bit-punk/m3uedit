package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SorterPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = SorterPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins)
                .hasSize(2)
                .extracting("name")
                .contains("ChannelName Sorter");
    }

    @Test
    void testTheNamesAreReadWithoutCreatingThePlugins() {
        List<String> allPluginNames = SorterPluginService.getInstance().getDescriptors().stream()
                .map(PluginDescriptor::name).toList();
        assertThat(allPluginNames)
                .hasSize(2)
                .contains("ChannelName Sorter");
    }
}
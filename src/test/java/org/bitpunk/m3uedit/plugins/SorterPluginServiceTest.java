package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SorterPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = SorterPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins, hasSize(1));
        assertThat(allPlugins, hasItem(hasProperty("name", is("ChannelName Sorter"))));
    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = SorterPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames, hasSize(1));
        assertThat(allPluginNames, hasItem("ChannelName Sorter"));
    }
}
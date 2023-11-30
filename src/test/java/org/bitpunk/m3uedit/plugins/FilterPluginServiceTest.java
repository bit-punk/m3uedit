package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FilterPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = FilterPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins, hasSize(5));
        assertThat(allPlugins, hasItem(hasProperty("name", is("ChannelName Filter"))));
        assertThat(allPlugins, hasItem(hasProperty("name", is("Reachable URL Filter"))));
        assertThat(allPlugins, hasItem(hasProperty("name", is("SD Channel Filter"))));
        assertThat(allPlugins, hasItem(hasProperty("name", is("Unique Name Filter"))));
        assertThat(allPlugins, hasItem(hasProperty("name", is("Unique URI Filter"))));
    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = FilterPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames, hasSize(5));
        assertThat(allPluginNames, hasItem("ChannelName Filter"));
        assertThat(allPluginNames, hasItem("Reachable URL Filter"));
        assertThat(allPluginNames, hasItem("SD Channel Filter"));
        assertThat(allPluginNames, hasItem("Unique Name Filter"));
        assertThat(allPluginNames, hasItem("Unique URI Filter"));
    }
}
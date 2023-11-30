package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class InputPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = InputPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins, hasSize(2));
        assertThat(allPlugins, hasItem(hasProperty("name", is("File Input"))));
        assertThat(allPlugins, hasItem(hasProperty("name", is("FritzBox Input"))));
    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = InputPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames, hasSize(2));
        assertThat(allPluginNames, hasItem("File Input"));
        assertThat(allPluginNames, hasItem("FritzBox Input"));
    }
}
package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = InputPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins).hasSize(3);
        assertThat(allPlugins).extracting("name").contains("File Input");
        assertThat(allPlugins).extracting("name").contains("FritzBox Input");
        assertThat(allPlugins).extracting("name").contains("Url Input");

    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = InputPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames).hasSize(3);
        assertThat(allPluginNames).contains("File Input");
        assertThat(allPluginNames).contains("FritzBox Input");
        assertThat(allPluginNames).contains("Url Input");
    }
}
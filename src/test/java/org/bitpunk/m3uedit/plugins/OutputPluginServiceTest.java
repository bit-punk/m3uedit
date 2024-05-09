package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = OutputPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins)
                .hasSize(2)
                .extracting("name")
                .contains("File Output", "SFTP Output");
    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = OutputPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames)
                .hasSize(2)
                .contains("File Output", "SFTP Output");
    }
}
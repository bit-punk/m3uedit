package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class OutputPluginServiceTest {

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = OutputPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins, hasSize(2));
        assertThat(allPlugins, hasItem(hasProperty("name", is("File Output"))));
        assertThat(allPlugins, hasItem(hasProperty("name", is("SFTP Output"))));
    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = OutputPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames, hasSize(2));
        assertThat(allPluginNames, hasItem("File Output"));
        assertThat(allPluginNames, hasItem("SFTP Output"));
    }
}
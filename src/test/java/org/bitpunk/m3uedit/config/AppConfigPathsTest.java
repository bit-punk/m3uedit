package org.bitpunk.m3uedit.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigPathsTest {

    @Test
    void testGetBaseDir() {
        Path expected = Paths.get(System.getProperty("user.home"), ".m3uedit");
        assertThat(AppConfigPaths.getBaseDir()).isEqualTo(expected);
    }

    @Test
    void testGetPluginsDir() {
        Path expected = Paths.get(System.getProperty("user.home"), ".m3uedit", "plugins");
        assertThat(AppConfigPaths.getPluginsDir()).isEqualTo(expected);
    }

    @Test
    void testGetPluginConfigPath() {
        Path expected = Paths.get(System.getProperty("user.home"), ".m3uedit", "plugins", "test_plugin.yaml");
        assertThat(AppConfigPaths.getPluginConfigPath("test_plugin.yaml")).isEqualTo(expected);
    }

    @Test
    void testGetAppConfigPath() {
        Path expected = Paths.get(System.getProperty("user.home"), ".m3uedit", "settings.yaml");
        assertThat(AppConfigPaths.getAppConfigPath("settings.yaml")).isEqualTo(expected);
    }
}

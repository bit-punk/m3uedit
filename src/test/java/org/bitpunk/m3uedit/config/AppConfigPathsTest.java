package org.bitpunk.m3uedit.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigPathsTest {

    private String configDirDuringTests;

    @BeforeEach
    void clearConfigDirProperty() {
        // set for all tests by TestConfigDirListener
        configDirDuringTests = System.clearProperty(AppConfigPaths.CONFIG_DIR_PROPERTY);
    }

    @AfterEach
    void restoreConfigDirProperty() {
        if (configDirDuringTests != null) {
            System.setProperty(AppConfigPaths.CONFIG_DIR_PROPERTY, configDirDuringTests);
        }
    }

    @Test
    void testTestsUseATemporaryConfigDir() {
        assertThat(configDirDuringTests).isNotBlank();
        assertThat(Path.of(configDirDuringTests)).isNotEqualTo(Paths.get(System.getProperty("user.home"), ".m3uedit"));
    }

    @Test
    void testGetBaseDir() {
        Path expected = Paths.get(System.getProperty("user.home"), ".m3uedit");
        assertThat(AppConfigPaths.getBaseDir()).isEqualTo(expected);
    }

    @Test
    void testGetBaseDirUsesConfigDirProperty(@TempDir Path tempDir) {
        System.setProperty(AppConfigPaths.CONFIG_DIR_PROPERTY, tempDir.toString());
        assertThat(AppConfigPaths.getBaseDir()).isEqualTo(tempDir);
        assertThat(AppConfigPaths.getPluginConfigPath("x.yaml")).isEqualTo(tempDir.resolve("plugins").resolve("x.yaml"));
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

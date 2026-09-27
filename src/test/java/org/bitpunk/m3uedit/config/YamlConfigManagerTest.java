package org.bitpunk.m3uedit.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YamlConfigManagerTest {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleConfig {
        private String name;
        private int count;
        private List<String> items;
    }

    @Test
    void testLoadFromResourceAndSaveToUserConfig(@TempDir Path tempDir) {
        Path targetPath = tempDir.resolve("plugins").resolve("preferred_channel_order.yaml");
        YamlConfigManager<SampleConfig> manager = new YamlConfigManager<>(
                targetPath,
                "plugin/sorter/preferredchannelorder/preferred_channel_order.yaml",
                SampleConfig.class
        );

        SampleConfig config = manager.loadConfig();
        assertThat(config).isNotNull();
        assertThat(Files.exists(targetPath)).isTrue();
    }

    @Test
    void testSaveAndLoadConfig(@TempDir Path tempDir) {
        Path targetPath = tempDir.resolve("sample.yaml");
        YamlConfigManager<SampleConfig> manager = new YamlConfigManager<>(
                targetPath,
                SampleConfig.class,
                () -> SampleConfig.builder().name("default").count(1).build()
        );

        SampleConfig sample = SampleConfig.builder()
                .name("custom")
                .count(42)
                .items(List.of("alpha", "beta"))
                .build();

        manager.saveConfig(sample);
        assertThat(Files.exists(targetPath)).isTrue();

        SampleConfig loaded = manager.loadConfig();
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("custom");
        assertThat(loaded.getCount()).isEqualTo(42);
        assertThat(loaded.getItems()).containsExactly("alpha", "beta");
    }

    @Test
    void testFallbackToSupplierWhenFileAndResourceNotFound(@TempDir Path tempDir) {
        Path targetPath = tempDir.resolve("non_existent.yaml");
        YamlConfigManager<SampleConfig> manager = new YamlConfigManager<>(
                targetPath,
                "non/existent/resource.yaml",
                SampleConfig.class,
                () -> SampleConfig.builder().name("fallback").count(99).build()
        );

        SampleConfig config = manager.loadConfig();
        assertThat(config).isNotNull();
        assertThat(config.getName()).isEqualTo("fallback");
        assertThat(config.getCount()).isEqualTo(99);
    }

    @Test
    void testFallbackToDefaultConstructorWhenNoSupplier(@TempDir Path tempDir) {
        Path targetPath = tempDir.resolve("non_existent.yaml");
        YamlConfigManager<SampleConfig> manager = new YamlConfigManager<>(
                targetPath,
                SampleConfig.class
        );

        SampleConfig config = manager.loadConfig();
        assertThat(config).isNotNull();
        assertThat(config.getName()).isNull();
        assertThat(config.getCount()).isEqualTo(0);
    }

    @Test
    void testLoadInvalidYamlFallsBackToDefault(@TempDir Path tempDir) throws IOException {
        Path targetPath = tempDir.resolve("corrupted.yaml");
        Files.writeString(targetPath, "invalid: : : yaml: [");

        YamlConfigManager<SampleConfig> manager = new YamlConfigManager<>(
                targetPath,
                SampleConfig.class,
                () -> SampleConfig.builder().name("recovered").count(5).build()
        );

        SampleConfig config = manager.loadConfig();
        assertThat(config).isNotNull();
        assertThat(config.getName()).isEqualTo("recovered");
        assertThat(config.getCount()).isEqualTo(5);
    }

    @Test
    void testSaveWithNullUserPathOrNullConfig() {
        YamlConfigManager<SampleConfig> nullPathManager = new YamlConfigManager<>(
                (Path) null,
                SampleConfig.class
        );
        // Should not throw
        nullPathManager.saveConfig(new SampleConfig());

        YamlConfigManager<SampleConfig> validPathManager = new YamlConfigManager<>(
                Path.of("some/path.yaml"),
                SampleConfig.class
        );
        // Should not throw
        validPathManager.saveConfig(null);
    }

    @Test
    void testPluginFileNameConstructor() {
        YamlConfigManager<SampleConfig> manager = new YamlConfigManager<>(
                "my_plugin.yaml",
                "some/resource.yaml",
                SampleConfig.class,
                () -> SampleConfig.builder().name("plugin").build()
        );

        assertThat(manager.getUserConfigPath()).isEqualTo(AppConfigPaths.getPluginConfigPath("my_plugin.yaml"));
        assertThat(manager.getResourcePath()).isEqualTo("some/resource.yaml");
        assertThat(manager.getConfigClass()).isEqualTo(SampleConfig.class);
    }
}

package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreferredChannelOrderConfigManagerTest {

    @Test
    void testLoadDefaultConfigFromResource(@TempDir Path tempDir) {
        Path nonExistentConfig = tempDir.resolve("plugins").resolve("preferred_channel_order.yaml");
        PreferredChannelOrderConfigManager manager = new PreferredChannelOrderConfigManager(nonExistentConfig);

        PreferredChannelOrderConfig config = manager.loadConfig();

        assertThat(config).isNotNull();
        assertThat(config.getDefaultProfile()).isEqualTo("deutschland_standard");
        assertThat(config.getProfiles()).isNotEmpty();
        assertThat(config.getProfiles())
                .extracting(ChannelProfile::getId)
                .contains("deutschland_standard", "sport_favoriten");

        // Should have created the initial file at the target path
        assertThat(Files.exists(nonExistentConfig)).isTrue();
    }

    @Test
    void testSaveAndLoadConfig(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("custom_order.yaml");
        PreferredChannelOrderConfigManager manager = new PreferredChannelOrderConfigManager(configFile);

        ChannelProfile profile = ChannelProfile.builder()
                .id("custom_profile")
                .name("Custom Profile")
                .channels(List.of("Channel 1", "Channel 2", "Channel 3"))
                .build();

        PreferredChannelOrderConfig config = PreferredChannelOrderConfig.builder()
                .defaultProfile("custom_profile")
                .profiles(List.of(profile))
                .build();

        manager.saveConfig(config);
        assertThat(Files.exists(configFile)).isTrue();

        PreferredChannelOrderConfig loaded = manager.loadConfig();
        assertThat(loaded).isNotNull();
        assertThat(loaded.getDefaultProfile()).isEqualTo("custom_profile");
        assertThat(loaded.getProfiles()).hasSize(1);
        assertThat(loaded.getProfiles().getFirst().getId()).isEqualTo("custom_profile");
        assertThat(loaded.getProfiles().getFirst().getChannels()).containsExactly("Channel 1", "Channel 2", "Channel 3");
    }
}

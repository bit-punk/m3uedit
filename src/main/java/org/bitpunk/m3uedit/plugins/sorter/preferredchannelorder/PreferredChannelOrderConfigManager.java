package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class PreferredChannelOrderConfigManager {
    private static final String RESOURCE_PATH = "plugin/sorter/preferredchannelorder/preferred_channel_order.yaml";
    private static final Path DEFAULT_USER_CONFIG_PATH = Paths.get(
            System.getProperty("user.home"), ".m3uedit", "plugins", "preferred_channel_order.yaml"
    );

    @Getter
    private final Path userConfigPath;
    private final ObjectMapper yamlMapper;

    public PreferredChannelOrderConfigManager() {
        this(DEFAULT_USER_CONFIG_PATH);
    }

    public PreferredChannelOrderConfigManager(Path userConfigPath) {
        this.userConfigPath = userConfigPath;
        this.yamlMapper = YAMLMapper.builder().build();
    }

    public PreferredChannelOrderConfig loadConfig() {
        try {
            if (userConfigPath != null && Files.exists(userConfigPath)) {
                log.debug("Loading user configuration from: {}", userConfigPath);
                return yamlMapper.readValue(userConfigPath.toFile(), PreferredChannelOrderConfig.class);
            }
            log.debug("Loading default configuration from classpath resource: {}", RESOURCE_PATH);
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
                if (inputStream == null) {
                    log.error("Resource not found: {}", RESOURCE_PATH);
                    return new PreferredChannelOrderConfig();
                }
                PreferredChannelOrderConfig config = yamlMapper.readValue(inputStream, PreferredChannelOrderConfig.class);
                if (userConfigPath != null) {
                    saveConfig(config);
                }
                return config;
            }
        } catch (Exception e) {
            log.error("Failed to load channel order configuration", e);
            return new PreferredChannelOrderConfig();
        }
    }

    public void saveConfig(PreferredChannelOrderConfig config) {
        if (userConfigPath == null || config == null) {
            return;
        }
        try {
            if (userConfigPath.getParent() != null) {
                Files.createDirectories(userConfigPath.getParent());
            }
            yamlMapper.writeValue(userConfigPath.toFile(), config);
            log.info("Saved channel order configuration to: {}", userConfigPath);
        } catch (Exception e) {
            log.error("Failed to save configuration to: {}", userConfigPath, e);
        }
    }
}

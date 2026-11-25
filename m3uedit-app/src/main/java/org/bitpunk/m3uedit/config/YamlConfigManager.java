package org.bitpunk.m3uedit.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
public class YamlConfigManager<T> {
    @Getter
    private final Path userConfigPath;
    @Getter
    private final String resourcePath;
    @Getter
    private final Class<T> configClass;
    private final Supplier<T> defaultInstanceSupplier;
    @Getter
    private final ObjectMapper yamlMapper;

    public YamlConfigManager(Path userConfigPath, String resourcePath, Class<T> configClass, Supplier<T> defaultInstanceSupplier) {
        this(userConfigPath, resourcePath, configClass, defaultInstanceSupplier, YAMLMapper.builder().build());
    }

    public YamlConfigManager(Path userConfigPath, String resourcePath, Class<T> configClass, Supplier<T> defaultInstanceSupplier, ObjectMapper yamlMapper) {
        this.userConfigPath = userConfigPath;
        this.resourcePath = resourcePath;
        this.configClass = Objects.requireNonNull(configClass, "configClass must not be null");
        this.defaultInstanceSupplier = defaultInstanceSupplier != null ? defaultInstanceSupplier : () -> createDefaultInstance(configClass);
        this.yamlMapper = yamlMapper != null ? yamlMapper : YAMLMapper.builder().build();
    }

    public YamlConfigManager(Path userConfigPath, String resourcePath, Class<T> configClass) {
        this(userConfigPath, resourcePath, configClass, null);
    }

    public YamlConfigManager(Path userConfigPath, Class<T> configClass, Supplier<T> defaultInstanceSupplier) {
        this(userConfigPath, null, configClass, defaultInstanceSupplier);
    }

    public YamlConfigManager(Path userConfigPath, Class<T> configClass) {
        this(userConfigPath, null, configClass, null);
    }

    public YamlConfigManager(String pluginConfigFileName, String resourcePath, Class<T> configClass, Supplier<T> defaultInstanceSupplier) {
        this(AppConfigPaths.getPluginConfigPath(pluginConfigFileName), resourcePath, configClass, defaultInstanceSupplier);
    }

    public YamlConfigManager(String pluginConfigFileName, String resourcePath, Class<T> configClass) {
        this(AppConfigPaths.getPluginConfigPath(pluginConfigFileName), resourcePath, configClass, null);
    }

    public T loadConfig() {
        try {
            if (userConfigPath != null && Files.exists(userConfigPath)) {
                log.debug("Loading user configuration from: {}", userConfigPath);
                return yamlMapper.readValue(userConfigPath.toFile(), configClass);
            }
            if (resourcePath != null) {
                log.debug("Loading default configuration from classpath resource: {}", resourcePath);
                try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (inputStream != null) {
                        T config = yamlMapper.readValue(inputStream, configClass);
                        if (userConfigPath != null) {
                            saveConfig(config);
                        }
                        return config;
                    }
                    log.warn("Resource not found: {}", resourcePath);
                }
            }
            return supplyDefaultInstance();
        } catch (Exception e) {
            log.error("Failed to load configuration from {}", userConfigPath != null ? userConfigPath : resourcePath, e);
            return supplyDefaultInstance();
        }
    }

    public void saveConfig(T config) {
        if (userConfigPath == null || config == null) {
            return;
        }
        try {
            if (userConfigPath.getParent() != null) {
                Files.createDirectories(userConfigPath.getParent());
            }
            yamlMapper.writeValue(userConfigPath.toFile(), config);
            log.info("Saved configuration to: {}", userConfigPath);
        } catch (Exception e) {
            log.error("Failed to save configuration to: {}", userConfigPath, e);
        }
    }

    private T supplyDefaultInstance() {
        if (defaultInstanceSupplier != null) {
            try {
                return defaultInstanceSupplier.get();
            } catch (Exception e) {
                log.error("Failed to supply default instance for class {}", configClass.getName(), e);
            }
        }
        return createDefaultInstance(configClass);
    }

    private static <T> T createDefaultInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("Could not instantiate default instance of {} using default constructor", clazz.getName(), e);
            return null;
        }
    }
}

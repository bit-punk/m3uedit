package org.bitpunk.m3uedit.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The general settings kept in {@code ~/.m3uedit/settings.yaml}. The file is read again before each change, so
 * changes made by hand are kept.
 */
public class Settings {
    public static final String CONFIG_FILE_NAME = "settings.yaml";

    private final YamlConfigManager<SettingsConfig> configManager;

    public Settings() {
        this(AppConfigPaths.getAppConfigPath(CONFIG_FILE_NAME));
    }

    public Settings(Path configFile) {
        this.configManager = new YamlConfigManager<>(configFile, SettingsConfig.class, SettingsConfig::new);
    }

    private SettingsConfig load() {
        SettingsConfig config = Objects.requireNonNullElseGet(configManager.loadConfig(), SettingsConfig::new);
        if (config.getStartup() == null) {
            config.setStartup(StartupMode.START_SCREEN);
        }
        if (config.getLanguage() == null) {
            config.setLanguage(Language.SYSTEM);
        }
        if (config.getOpenProjects() == null) {
            config.setOpenProjects(new ArrayList<>());
        }
        return config;
    }

    public StartupMode getStartup() {
        return load().getStartup();
    }

    public void setStartup(StartupMode startup) {
        SettingsConfig config = load();
        config.setStartup(Objects.requireNonNull(startup));
        configManager.saveConfig(config);
    }

    public Language getLanguage() {
        return load().getLanguage();
    }

    public void setLanguage(Language language) {
        SettingsConfig config = load();
        config.setLanguage(Objects.requireNonNull(language));
        configManager.saveConfig(config);
    }

    /**
     * Remembers the project-files open while m3uedit is closed.
     */
    public void rememberOpenProjects(List<Path> projectFiles) {
        SettingsConfig config = load();
        config.setOpenProjects(projectFiles.stream().map(file -> file.toAbsolutePath().normalize().toString())
                .toList());
        configManager.saveConfig(config);
    }

    /**
     * @return the project-files to open at startup: those open when m3uedit was closed, if
     * {@link StartupMode#REOPEN_PROJECTS} is set, without the files that no longer exist
     */
    public List<Path> projectsToReopen() {
        SettingsConfig config = load();
        if (config.getStartup() != StartupMode.REOPEN_PROJECTS) {
            return List.of();
        }
        return config.getOpenProjects().stream().filter(Objects::nonNull).filter(path -> !path.isBlank())
                .map(Path::of).filter(Files::isRegularFile).distinct().toList();
    }
}

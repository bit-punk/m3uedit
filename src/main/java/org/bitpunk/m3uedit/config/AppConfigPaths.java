package org.bitpunk.m3uedit.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppConfigPaths {
    private static final String APP_DIR_NAME = ".m3uedit";
    private static final String PLUGINS_DIR_NAME = "plugins";

    private AppConfigPaths() {
        // Utility class
    }

    public static Path getBaseDir() {
        return Paths.get(System.getProperty("user.home"), APP_DIR_NAME);
    }

    public static Path getPluginsDir() {
        return getBaseDir().resolve(PLUGINS_DIR_NAME);
    }

    public static Path getPluginConfigPath(String fileName) {
        return getPluginsDir().resolve(fileName);
    }

    public static Path getAppConfigPath(String fileName) {
        return getBaseDir().resolve(fileName);
    }
}

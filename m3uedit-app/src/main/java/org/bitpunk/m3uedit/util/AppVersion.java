package org.bitpunk.m3uedit.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The version and the build time of the application, written into {@code m3uedit.properties} by the build: the
 * version of the release tag for releases (e.g. 1.2.0), or the snapshot version of the {@code pom.xml}.
 */
public final class AppVersion {
    private static final String RESOURCE = "/m3uedit.properties";
    private static final String UNKNOWN = "unknown";
    private static final Properties PROPERTIES = load();

    private AppVersion() {
        // Utility class
    }

    /**
     * @return e.g. "1.2.0" or "1.2.0-SNAPSHOT", "unknown" if not built by Maven (e.g. started from the IDE without)
     */
    public static String version() {
        return value("version");
    }

    /**
     * @return the time of the build, e.g. "2026-11-19T10:15:00Z", "unknown" if not built by Maven
     */
    public static String buildTime() {
        return value("build.time");
    }

    private static String value(String key) {
        String value = PROPERTIES.getProperty(key);
        // not filtered, e.g. the resource is used directly from the sources
        return value == null || value.isBlank() || value.contains("${") ? UNKNOWN : value;
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream inputStream = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            // the version is not essential, it is shown as unknown
        }
        return properties;
    }
}

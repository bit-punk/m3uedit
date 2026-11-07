package org.bitpunk.m3uedit.testsupport;

import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Lets all tests use a temporary configuration directory instead of the user's {@code ~/.m3uedit}, so tests neither
 * depend on nor modify the user's configuration.
 * <p>
 * Registered via {@code META-INF/services}, so it is active for Maven as well as for test runs within the IDE.
 */
public class TestConfigDirListener implements LauncherSessionListener {
    private Path configDir;
    private String previousValue;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try {
            configDir = Files.createTempDirectory("m3uedit-test-config");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create temporary configuration directory for tests", e);
        }
        previousValue = System.setProperty(AppConfigPaths.CONFIG_DIR_PROPERTY, configDir.toString());
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (previousValue == null) {
            System.clearProperty(AppConfigPaths.CONFIG_DIR_PROPERTY);
        } else {
            System.setProperty(AppConfigPaths.CONFIG_DIR_PROPERTY, previousValue);
        }
        deleteRecursively(configDir);
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            // only a temporary directory, nothing to worry about
        }
    }
}

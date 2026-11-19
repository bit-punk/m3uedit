package org.bitpunk.m3uedit.util;

/**
 * Whether the application runs without user interface, started from the command line (see
 * {@link org.bitpunk.m3uedit.cli.CommandLineRunner}): plugins must not show dialogs then.
 */
public final class HeadlessMode {
    private static volatile boolean active;

    private HeadlessMode() {
        // Utility class
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        HeadlessMode.active = active;
    }
}

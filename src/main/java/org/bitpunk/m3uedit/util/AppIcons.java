package org.bitpunk.m3uedit.util;

import javafx.collections.ListChangeListener;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The icon of the application, shown by the window system for its windows. Only to be used on the JavaFX
 * application thread.
 */
public final class AppIcons {
    private static final List<Integer> SIZES = List.of(16, 32, 48, 64, 128, 256);
    private static List<Image> icons;
    private static boolean installed;

    private AppIcons() {
        // Utility class
    }

    /**
     * @return the icon in all sizes provided
     */
    public static List<Image> get() {
        if (icons == null) {
            icons = SIZES.stream().map(size -> new Image(Objects.requireNonNull(
                    AppIcons.class.getResourceAsStream("/icons/app/m3uedit-" + size + ".png")))).toList();
        }
        return icons;
    }

    /**
     * Gives each window opened from now on the icon of the application, unless it has an icon of its own: without,
     * the window system shows a default icon for dialogs, alerts and other windows.
     */
    public static void applyToAllWindows() {
        if (installed) {
            return;
        }
        installed = true;
        Window.getWindows().forEach(AppIcons::apply);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(AppIcons::apply);
            }
        });
    }

    private static void apply(Window window) {
        if (window instanceof Stage stage && stage.getIcons().isEmpty()) {
            stage.getIcons().addAll(get());
        }
    }
}

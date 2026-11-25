package org.bitpunk.m3uedit.util;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.bitpunk.m3uedit.config.Theme;

import java.util.Objects;

/**
 * Applies the theme to all windows of the application: the main window, dialogs, the programme guide and popups.
 * <p>
 * Each scene gets the stylesheet {@code fxml/css/theme.css}, which defines the colours of the application. With the
 * dark theme, the root of each scene gets the style class {@code dark}, which switches the colours of Modena and of
 * the stylesheets of the application (e.g. {@code .dark .project} in {@code project.css}).
 */
public final class Themes {
    public static final String STYLESHEET = Objects.requireNonNull(
            Themes.class.getResource("/fxml/css/theme.css")).toExternalForm();
    public static final String DARK_STYLE_CLASS = "dark";
    /**
     * The style of an input field containing an invalid value, using the colours of the theme.
     */
    public static final String INVALID_INPUT_STYLE = "-fx-text-box-border: -app-error; -fx-focus-color: -app-error; "
            + "-fx-control-inner-background: -app-invalid-background; -fx-accent: -app-error;";
    private static final String STYLED = Themes.class.getName() + ".styled";

    private static final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);
    /**
     * The colour scheme of the operating system, bound to the platform preferences when installed.
     */
    static final ObjectProperty<ColorScheme> systemColorScheme = new SimpleObjectProperty<>(ColorScheme.LIGHT);
    private static final BooleanBinding dark = Bindings.createBooleanBinding(
            () -> theme.get() == Theme.DARK
                    || theme.get() == Theme.SYSTEM && systemColorScheme.get() == ColorScheme.DARK,
            theme, systemColorScheme);
    private static boolean installed;

    private Themes() {
        // Utility class
    }

    /**
     * Applies the theme to the windows shown now and to all windows shown later; called once on the JavaFX thread.
     */
    public static void install(Theme initial) {
        setTheme(initial);
        if (installed) {
            return;
        }
        installed = true;
        systemColorScheme.bind(Platform.getPreferences().colorSchemeProperty());
        dark.addListener((observable, wasDark, isDark) -> Window.getWindows().forEach(Themes::applyTo));
        Window.getWindows().forEach(Themes::applyTo);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(Themes::applyTo);
            }
        });
    }

    /**
     * Changes the theme of all windows at once.
     */
    public static void setTheme(Theme newTheme) {
        theme.set(Objects.requireNonNull(newTheme));
    }

    public static Theme getTheme() {
        return theme.get();
    }

    /**
     * @return true while the windows are dark, e.g. with {@link Theme#SYSTEM} on a dark system
     */
    public static boolean isDark() {
        return dark.get();
    }

    static void applyTo(Window window) {
        if (window.getScene() != null) {
            applyTo(window.getScene());
        }
        if (!window.getProperties().containsKey(STYLED)) {
            window.getProperties().put(STYLED, Boolean.TRUE);
            window.sceneProperty().addListener((observable, oldScene, scene) -> {
                if (scene != null) {
                    applyTo(scene);
                }
            });
        }
    }

    /**
     * Gives the scene the stylesheet of the theme and marks its root as dark (or not).
     */
    public static void applyTo(Scene scene) {
        if (!scene.getStylesheets().contains(STYLESHEET)) {
            try {
                // first, so the stylesheets of the windows can override it
                scene.getStylesheets().addFirst(STYLESHEET);
            } catch (RuntimeException e) {
                // the stylesheets of a popup are bound to the ones of its owner, which has the stylesheet already
            }
        }
        if (!scene.getProperties().containsKey(STYLED)) {
            scene.getProperties().put(STYLED, Boolean.TRUE);
            scene.rootProperty().addListener((observable, oldRoot, root) -> markDark(root));
        }
        markDark(scene.getRoot());
    }

    private static void markDark(Parent root) {
        if (root == null) {
            return;
        }
        if (dark.get()) {
            if (!root.getStyleClass().contains(DARK_STYLE_CLASS)) {
                root.getStyleClass().add(DARK_STYLE_CLASS);
            }
        } else {
            root.getStyleClass().remove(DARK_STYLE_CLASS);
        }
    }
}

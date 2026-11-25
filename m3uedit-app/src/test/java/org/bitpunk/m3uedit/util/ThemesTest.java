package org.bitpunk.m3uedit.util;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.config.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ThemesTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
        Platform.setImplicitExit(false);
        onFxThread(() -> {
            Themes.install(Theme.LIGHT);
            // the colour scheme of the system is set by the tests
            Themes.systemColorScheme.unbind();
            Themes.systemColorScheme.set(ColorScheme.LIGHT);
            return null;
        });
    }

    @AfterEach
    void light() throws Exception {
        onFxThread(() -> {
            Themes.setTheme(Theme.LIGHT);
            Themes.systemColorScheme.set(ColorScheme.LIGHT);
            return null;
        });
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static Stage window() {
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(new Label("m3uedit")), 200, 100));
        stage.show();
        return stage;
    }

    private static boolean isDark(Scene scene) {
        return scene.getRoot().getStyleClass().contains(Themes.DARK_STYLE_CLASS);
    }

    @Test
    void testEveryWindowGetsTheStylesheetOfTheTheme() throws Exception {
        List<String> stylesheets = onFxThread(() -> {
            Stage stage = window();
            try {
                return List.copyOf(stage.getScene().getStylesheets());
            } finally {
                stage.close();
            }
        });

        assertThat(stylesheets).containsExactly(Themes.STYLESHEET);
    }

    @Test
    void testAChangedThemeIsAppliedToTheOpenWindowsAtOnce() throws Exception {
        List<Boolean> dark = onFxThread(() -> {
            Stage stage = window();
            try {
                boolean light = isDark(stage.getScene());
                Themes.setTheme(Theme.DARK);
                boolean switched = isDark(stage.getScene());
                Themes.setTheme(Theme.LIGHT);
                return List.of(light, switched, isDark(stage.getScene()));
            } finally {
                stage.close();
            }
        });

        assertThat(dark).containsExactly(false, true, false);
    }

    @Test
    void testWindowsOpenedLaterAreDarkToo() throws Exception {
        List<Boolean> dark = onFxThread(() -> {
            Themes.setTheme(Theme.DARK);
            Stage stage = window();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "text", ButtonType.OK);
            alert.show();
            try {
                return List.of(isDark(stage.getScene()), isDark(alert.getDialogPane().getScene()),
                        alert.getDialogPane().getScene().getStylesheets().contains(Themes.STYLESHEET));
            } finally {
                alert.close();
                stage.close();
            }
        });

        assertThat(dark).containsExactly(true, true, true);
    }

    @Test
    void testTheSystemThemeFollowsTheOperatingSystem() throws Exception {
        List<Boolean> dark = onFxThread(() -> {
            Stage stage = window();
            try {
                Themes.setTheme(Theme.SYSTEM);
                boolean lightSystem = isDark(stage.getScene());
                // e.g. the user switches the desktop to dark while m3uedit is running
                Themes.systemColorScheme.set(ColorScheme.DARK);
                return List.of(lightSystem, isDark(stage.getScene()), Themes.isDark());
            } finally {
                stage.close();
            }
        });

        assertThat(dark).containsExactly(false, true, true);
    }

    @Test
    void testTheLightAndDarkThemesIgnoreTheOperatingSystem() throws Exception {
        List<Boolean> dark = onFxThread(() -> {
            Themes.systemColorScheme.set(ColorScheme.DARK);
            Themes.setTheme(Theme.LIGHT);
            boolean light = Themes.isDark();
            Themes.systemColorScheme.set(ColorScheme.LIGHT);
            Themes.setTheme(Theme.DARK);
            return List.of(light, Themes.isDark());
        });

        assertThat(dark).containsExactly(false, true);
    }
}

package org.bitpunk.m3uedit.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.controller.AboutDialogController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AppIconsTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
        // closing the last window must not end the toolkit for the following tests
        Platform.setImplicitExit(false);
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

    @Test
    void testTheIconIsProvidedInAllSizes() throws Exception {
        List<Image> icons = onFxThread(AppIcons::get);

        assertThat(icons).hasSize(6).allMatch(icon -> !icon.isError());
        assertThat(icons).extracting(Image::getWidth).containsExactly(16.0, 32.0, 48.0, 64.0, 128.0, 256.0);
    }

    @Test
    void testWindowsOpenedLaterGetTheIcon() throws Exception {
        List<Integer> iconCounts = onFxThread(() -> {
            AppIcons.applyToAllWindows();
            AboutDialogController<Void> dialog = new AboutDialogController<>();
            dialog.show();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "test");
            alert.show();
            Stage stage = new Stage();
            stage.show();
            Stage own = new Stage();
            Image icon = AppIcons.get().getFirst();
            own.getIcons().add(icon);
            own.show();
            List<Integer> counts = List.of(((Stage) dialog.getDialogPane().getScene().getWindow()).getIcons().size(),
                    ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().size(),
                    stage.getIcons().size(), own.getIcons().size());
            dialog.close();
            alert.close();
            stage.close();
            own.close();
            return counts;
        });

        // a window with an icon of its own keeps it
        assertThat(iconCounts).containsExactly(6, 6, 6, 1);
    }
}

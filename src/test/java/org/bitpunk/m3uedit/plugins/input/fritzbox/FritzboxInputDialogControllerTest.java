package org.bitpunk.m3uedit.plugins.input.fritzbox;

import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FritzboxInputDialogControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private void runOnFxThread(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

    @Test
    void testDialogInitialization() throws Exception {
        runOnFxThread(() -> {
            FritzboxInputDialogController controller = new FritzboxInputDialogController();

            assertThat(controller.getTitle()).isEqualTo(FritzboxInputDialogController.FRITZBOX_DIALOG_TITLE);
            assertThat(controller.getDialogPane()).isNotNull();

            TextField baseUrlTextField = getField(controller, "baseUrlTextField");
            assertThat(baseUrlTextField.getText()).isEqualTo("http://fritz.box/dvb/m3u/");

            ListView<StreamSource> availableStreamSources = getField(controller, "availableStreamSources");
            assertThat(availableStreamSources.getItems()).hasSize(2);
            assertThat(availableStreamSources.getItems().get(0).getName()).isEqualTo("tvsd.m3u");
            assertThat(availableStreamSources.getItems().get(1).getName()).isEqualTo("tvhd.m3u");

            assertThat(controller.getInputsFull().get()).isTrue();
        });
    }

    @Test
    void testValidationBinding() throws Exception {
        runOnFxThread(() -> {
            FritzboxInputDialogController controller = new FritzboxInputDialogController();
            TextField baseUrlTextField = getField(controller, "baseUrlTextField");

            baseUrlTextField.setText("");
            assertThat(controller.getInputsFull().get()).isFalse();

            baseUrlTextField.setText("invalid-url");
            assertThat(controller.getInputsFull().get()).isFalse();

            baseUrlTextField.setText("http://192.168.178.1/dvb/m3u/");
            assertThat(controller.getInputsFull().get()).isTrue();
        });
    }

    @Test
    void testResultConverter() throws Exception {
        runOnFxThread(() -> {
            FritzboxInputDialogController controller = new FritzboxInputDialogController();
            TextField baseUrlTextField = getField(controller, "baseUrlTextField");
            baseUrlTextField.setText("http://192.168.1.1/dvb/m3u/");

            ButtonType okButtonType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
            FritzBoxConnection result = controller.getResultConverter().call(okButtonType);

            assertThat(result).isNotNull();
            assertThat(result.getBaseUrl()).isEqualTo("http://192.168.1.1/dvb/m3u/");

            ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            FritzBoxConnection cancelResult = controller.getResultConverter().call(cancelButtonType);
            assertThat(cancelResult).isNull();
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

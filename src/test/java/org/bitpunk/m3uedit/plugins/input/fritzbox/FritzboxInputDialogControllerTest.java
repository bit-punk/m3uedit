package org.bitpunk.m3uedit.plugins.input.fritzbox;

import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
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
            FritzboxInputDialogController controller = new FritzboxInputDialogController(new DefaultPluginContext(FritzBoxInput.class));

            assertThat(controller.getTitle()).isEqualTo("Enter FritzBox-Inputs data");
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
            FritzboxInputDialogController controller = new FritzboxInputDialogController(new DefaultPluginContext(FritzBoxInput.class));
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
            FritzboxInputDialogController controller = new FritzboxInputDialogController(new DefaultPluginContext(FritzBoxInput.class));
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

    @Test
    void testTheSelectedStreamsAreNotDuplicatedByRefreshingTheList() throws Exception {
        runOnFxThread(() -> {
            FritzboxInputDialogController controller = new FritzboxInputDialogController(new DefaultPluginContext(FritzBoxInput.class));
            ListView<StreamSource> list = getField(controller, "availableStreamSources");
            new javafx.scene.Scene(new javafx.scene.layout.StackPane(list), 300, 200).getRoot().applyCss();
            // the cells are updated several times, e.g. while scrolling
            for (int i = 0; i < 3; i++) {
                list.refresh();
                list.layout();
            }

            assertThat(controller.getSelectedStreamSources()).extracting(StreamSource::getName)
                    .containsExactly("tvsd.m3u", "tvhd.m3u");
            assertThat(controller.getResultConverter().call(new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE))
                    .getSelectedStreamSources()).hasSize(2);
        });
    }

    @Test
    void testARecentlyUsedConnectionFillsInTheFields(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        RecentFritzBoxConnections recent = new RecentFritzBoxConnections(dir.resolve("recent.yaml"));
        recent.recordSuccess(RecentFritzBoxConnectionsTest.connection("http://192.168.178.1/dvb/m3u/",
                RecentFritzBoxConnectionsTest.HD));
        runOnFxThread(() -> {
            FritzboxInputDialogController controller = new FritzboxInputDialogController(new DefaultPluginContext(FritzBoxInput.class));
            controller.initializeRecentConnections(recent);
            javafx.scene.control.ComboBox<FritzBoxConnection> combo = getField(controller, "recentConnectionsComboBox");
            assertThat(combo.isDisabled()).isFalse();
            assertThat(combo.getConverter().toString(combo.getItems().getFirst()))
                    .isEqualTo("http://192.168.178.1/dvb/m3u/  (HD)");

            combo.getSelectionModel().selectFirst();
            combo.getOnAction().handle(new javafx.event.ActionEvent());

            TextField baseUrl = getField(controller, "baseUrlTextField");
            assertThat(baseUrl.getText()).isEqualTo("http://192.168.178.1/dvb/m3u/");
            assertThat(controller.getSelectedStreamSources()).extracting(StreamSource::getName)
                    .containsExactly("tvhd.m3u");
        });
    }

    @Test
    void testARecentlyUsedConnectionIsRemoved(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        RecentFritzBoxConnections recent = new RecentFritzBoxConnections(dir.resolve("recent.yaml"));
        recent.recordSuccess(RecentFritzBoxConnectionsTest.connection("http://fritz.box/dvb/m3u/"));
        runOnFxThread(() -> {
            FritzboxInputDialogController controller = new FritzboxInputDialogController(new DefaultPluginContext(FritzBoxInput.class));
            controller.initializeRecentConnections(recent);
            javafx.scene.control.ComboBox<FritzBoxConnection> combo = getField(controller, "recentConnectionsComboBox");
            javafx.scene.control.Button remove = getField(controller, "removeRecentConnectionButton");
            assertThat(remove.isDisabled()).as("nothing selected").isTrue();

            combo.getSelectionModel().selectFirst();
            remove.fire();

            assertThat(combo.getItems()).isEmpty();
            assertThat(combo.isDisabled()).isTrue();
        });
        assertThat(recent.getConnections()).isEmpty();
    }

    @Test
    void testTheDialogShowsTheCurrentConnection() throws Exception {
        runOnFxThread(() -> {
            FritzboxInputDialogController controller = new FritzboxInputDialogController(new DefaultPluginContext(FritzBoxInput.class));

            controller.setFritzBoxConnection(RecentFritzBoxConnectionsTest.connection("http://192.168.178.1/dvb/m3u/",
                    RecentFritzBoxConnectionsTest.HD));

            TextField baseUrl = getField(controller, "baseUrlTextField");
            assertThat(baseUrl.getText()).isEqualTo("http://192.168.178.1/dvb/m3u/");
            assertThat(controller.getSelectedStreamSources()).extracting(StreamSource::getName)
                    .containsExactly("tvhd.m3u");
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

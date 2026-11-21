package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import javafx.application.Platform;
import javafx.scene.Node;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.plugins.filter.reachableurl.model.ReachableUrlFilterConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReachableUrlFilterConfigDialogControllerTest {

    @TempDir
    Path tempDir;
    private Path configFile;
    private ReachableUrlFilter filter;

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @BeforeEach
    void setUp() {
        configFile = tempDir.resolve(ReachableUrlFilter.CONFIG_FILE_NAME);
        filter = new ReachableUrlFilter(new YamlConfigManager<>(configFile, ReachableUrlFilter.RESOURCE_PATH,
                ReachableUrlFilterConfig.class, ReachableUrlFilterConfig::new));
    }

    private interface FxAction {
        void run() throws Exception;
    }

    private void runOnFxThread(FxAction action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static Node okButton(ReachableUrlFilterConfigDialogController dialog) {
        return dialog.getDialogPane().lookupButton(dialog.getOkButtonType());
    }

    @Test
    void testDialogShowsConfiguredTimeout() throws Exception {
        Files.writeString(configFile, "timeoutMillis: 3500\n");
        runOnFxThread(() -> {
            var dialog = new ReachableUrlFilterConfigDialogController(filter);

            assertThat(dialog.getTitle()).isEqualTo("Configure Reachable URL Filter");
            assertThat(dialog.getTimeoutSpinner().getValue()).isEqualTo(3500);
            assertThat(okButton(dialog).isDisabled()).isFalse();
        });
    }

    @Test
    void testDialogShowsDefaultWithoutConfigFile() throws Exception {
        runOnFxThread(() -> {
            var dialog = new ReachableUrlFilterConfigDialogController(filter);
            assertThat(dialog.getTimeoutSpinner().getValue()).isEqualTo(ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS);
        });
    }

    @Test
    void testOutOfRangeConfigValueIsClampedToSpinnerRange() throws Exception {
        Files.writeString(configFile, "timeoutMillis: 999999\n");
        runOnFxThread(() -> {
            var dialog = new ReachableUrlFilterConfigDialogController(filter);
            assertThat(dialog.getTimeoutSpinner().getValue())
                    .isEqualTo(ReachableUrlFilterConfigDialogController.MAX_TIMEOUT_MILLIS);
        });
    }

    @Test
    void testTypedValueIsPersistedAndUsedByFilter() throws Exception {
        runOnFxThread(() -> {
            var dialog = new ReachableUrlFilterConfigDialogController(filter);
            dialog.getTimeoutSpinner().getEditor().setText(" 750 ");

            ReachableUrlFilterConfig result = dialog.applyAndPersistConfig();

            assertThat(result.getTimeoutMillis()).isEqualTo(750);
        });
        assertThat(Files.readString(configFile)).contains("timeoutMillis: 750");
        assertThat(filter.getTimeoutMillis()).isEqualTo(750);
    }

    @Test
    void testSpinnerIncrementIsPersisted() throws Exception {
        runOnFxThread(() -> {
            var dialog = new ReachableUrlFilterConfigDialogController(filter);
            dialog.getTimeoutSpinner().increment(5);   // default + 5 * 100
            dialog.applyAndPersistConfig();
        });
        assertThat(filter.getTimeoutMillis()).isEqualTo(ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS + 500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "12.5", "50", "60001", "-1"})
    void testInvalidInputDisablesOkButton(String input) throws Exception {
        runOnFxThread(() -> {
            var dialog = new ReachableUrlFilterConfigDialogController(filter);
            dialog.getTimeoutSpinner().getEditor().setText(input);

            assertThat(dialog.parseEditorText()).isNull();
            assertThat(okButton(dialog).isDisabled()).isTrue();

            dialog.getTimeoutSpinner().getEditor().setText("1200");
            assertThat(okButton(dialog).isDisabled()).isFalse();
        });
    }

    @Test
    void testCancelDoesNotWriteConfig() throws Exception {
        runOnFxThread(() -> {
            var dialog = new ReachableUrlFilterConfigDialogController(filter);
            dialog.getTimeoutSpinner().getEditor().setText("900");
            dialog.setResult(null);   // simulates closing the dialog via "Cancel"
        });
        assertThat(filter.getTimeoutMillis()).isEqualTo(ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS);
    }

    @Test
    void testFilterIsConfigurable() {
        assertThat(filter.isConfigurable()).isTrue();
    }

    @Test
    void testDialogFxmlIsOnClasspath() throws IOException {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream(ReachableUrlFilterConfigDialogController.FXML_PLUGIN_FILTER_CONFIG)) {
            assertThat(stream).isNotNull();
        }
    }
}

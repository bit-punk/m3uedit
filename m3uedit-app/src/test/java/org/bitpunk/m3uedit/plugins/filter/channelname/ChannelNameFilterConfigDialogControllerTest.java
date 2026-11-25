package org.bitpunk.m3uedit.plugins.filter.channelname;

import javafx.application.Platform;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.testsupport.ConfigDirectoryContext;
import org.bitpunk.m3uedit.plugins.filter.channelname.model.ChannelNameFilterConfig;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelNameFilterConfigDialogControllerTest {

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

    private ChannelNameFilter createSampleFilter(Path tempDir) {
        Path configPath = tempDir.resolve(ChannelNameFilter.CONFIG_FILE_NAME);
        ChannelNameFilterConfig config = ChannelNameFilterConfig.builder()
                .excludeRegexes(new ArrayList<>(List.of(".*QVC.*", ".*HSE.*", ".*1-2-3.*")))
                .build();
        YamlConfigManager<ChannelNameFilterConfig> manager = new YamlConfigManager<>(configPath, ChannelNameFilterConfig.class);
        manager.saveConfig(config);
        return ConfigDirectoryContext.create(ChannelNameFilter::new, tempDir);
    }

    @Test
    void testDialogInitialization() throws Exception {
        runOnFxThread(() -> {
            ChannelNameFilter filter = TestPluginSupport.create(ChannelNameFilter::new);
            ChannelNameFilterConfigDialogController controller = new ChannelNameFilterConfigDialogController(filter);

            assertThat(controller.getPatternsListView()).isNotNull();
            assertThat(controller.getPatternsListView().getItems()).isNotEmpty();
            assertThat(controller.getPatternsListView().getItems()).contains("(?i).*QVC.*", ".*HSE.*", ".*1-2-3.*", "^\\.+$");
        });
    }

    @Test
    void testAddAndRemovePattern(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            ChannelNameFilter filter = createSampleFilter(tempDir);
            ChannelNameFilterConfigDialogController controller = new ChannelNameFilterConfigDialogController(filter);

            assertThat(controller.getPatternsListView().getItems()).containsExactly(".*QVC.*", ".*HSE.*", ".*1-2-3.*");

            // Add new pattern
            controller.getNewPatternTextField().setText(".*Sky.*");
            controller.addPattern();

            assertThat(controller.getPatternsListView().getItems()).containsExactly(".*QVC.*", ".*HSE.*", ".*1-2-3.*", ".*Sky.*");
            assertThat(controller.getNewPatternTextField().getText()).isEmpty();
            assertThat(controller.getPatternsListView().getSelectionModel().getSelectedItem()).isEqualTo(".*Sky.*");

            // Remove selected pattern
            controller.removeSelectedPattern();
            assertThat(controller.getPatternsListView().getItems()).containsExactly(".*QVC.*", ".*HSE.*", ".*1-2-3.*");
        });
    }

    @Test
    void testMoveUpAndDown(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            ChannelNameFilter filter = createSampleFilter(tempDir);
            ChannelNameFilterConfigDialogController controller = new ChannelNameFilterConfigDialogController(filter);

            // Select 3rd item (.*1-2-3.*) and move it up
            controller.getPatternsListView().getSelectionModel().select(2);
            controller.moveUp();
            assertThat(controller.getPatternsListView().getItems()).containsExactly(".*QVC.*", ".*1-2-3.*", ".*HSE.*");
            assertThat(controller.getPatternsListView().getSelectionModel().getSelectedIndex()).isEqualTo(1);

            // Move 1st item down
            controller.getPatternsListView().getSelectionModel().select(0);
            controller.moveDown();
            assertThat(controller.getPatternsListView().getItems()).containsExactly(".*1-2-3.*", ".*QVC.*", ".*HSE.*");
            assertThat(controller.getPatternsListView().getSelectionModel().getSelectedIndex()).isEqualTo(1);
        });
    }

    @Test
    void testSaveAndApplyConfig(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            ChannelNameFilter filter = createSampleFilter(tempDir);
            ChannelNameFilterConfigDialogController controller = new ChannelNameFilterConfigDialogController(filter);

            // Add new pattern
            controller.getNewPatternTextField().setText(".*Test.*");
            controller.addPattern();

            // Save and apply
            controller.saveWorkingState();
            controller.applyAndPersistConfig();

            assertThat(filter.getExcludeRegexes()).containsExactly(".*QVC.*", ".*HSE.*", ".*1-2-3.*", ".*Test.*");

            // Verify filtering uses updated regexes
            Entry e1 = new Entry();
            e1.setName("Test Channel");
            Entry e2 = new Entry();
            e2.setName("ARD");

            List<Entry> filtered = filter.filter(List.of(e1, e2));
            assertThat(filtered).extracting(Entry::getName).containsExactly("ARD");

            // Verify persisted to file
            ChannelNameFilterConfig loaded = new YamlConfigManager<>(tempDir.resolve(ChannelNameFilter.CONFIG_FILE_NAME),
                    ChannelNameFilterConfig.class).loadConfig();
            assertThat(loaded.getExcludeRegexes()).containsExactly(".*QVC.*", ".*HSE.*", ".*1-2-3.*", ".*Test.*");
        });
    }

    @Test
    void testBindings(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            ChannelNameFilter filter = createSampleFilter(tempDir);
            ChannelNameFilterConfigDialogController controller = new ChannelNameFilterConfigDialogController(filter);

            // Initially text field is empty -> add button is disabled
            assertThat(controller.getAddPatternButton().isDisable()).isTrue();
            controller.getNewPatternTextField().setText("Some Pattern");
            assertThat(controller.getAddPatternButton().isDisable()).isFalse();

            // With no selection -> remove, moveUp, moveDown are disabled
            controller.getPatternsListView().getSelectionModel().clearSelection();
            assertThat(controller.getRemovePatternButton().isDisable()).isTrue();
            assertThat(controller.getMoveUpButton().isDisable()).isTrue();
            assertThat(controller.getMoveDownButton().isDisable()).isTrue();

            // Select first item
            controller.getPatternsListView().getSelectionModel().select(0);
            assertThat(controller.getRemovePatternButton().isDisable()).isFalse();
            assertThat(controller.getMoveUpButton().isDisable()).isTrue();
            assertThat(controller.getMoveDownButton().isDisable()).isFalse();

            // Select last item
            controller.getPatternsListView().getSelectionModel().select(2);
            assertThat(controller.getMoveUpButton().isDisable()).isFalse();
            assertThat(controller.getMoveDownButton().isDisable()).isTrue();
        });
    }
}

package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import javafx.application.Platform;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PreferredChannelOrderConfigDialogControllerTest {

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

    private PreferredChannelOrderSorter createSampleSorter(Path tempDir) {
        Path configFile = tempDir.resolve("config.yaml");
        YamlConfigManager<PreferredChannelOrderConfig> manager = new YamlConfigManager<>(configFile, PreferredChannelOrderConfig.class);

        ChannelProfile profile1 = ChannelProfile.builder()
                .id("profile1")
                .name("Profile One")
                .channels(new ArrayList<>(List.of("Das Erste HD", "ZDF HD", "RTL")))
                .build();

        ChannelProfile profile2 = ChannelProfile.builder()
                .id("profile2")
                .name("Profile Two")
                .channels(new ArrayList<>(List.of("SPORT1", "Eurosport 1")))
                .build();

        PreferredChannelOrderConfig config = PreferredChannelOrderConfig.builder()
                .defaultProfile("profile1")
                .profiles(new ArrayList<>(List.of(profile1, profile2)))
                .build();
        manager.saveConfig(config);

        PreferredChannelOrderSorter sorter = new PreferredChannelOrderSorter(manager);
        sorter.setSelectedProfileId("profile1");
        return sorter;
    }

    @Test
    void testDialogInitialization(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            PreferredChannelOrderSorter sorter = createSampleSorter(tempDir);
            PreferredChannelOrderConfigDialogController controller = new PreferredChannelOrderConfigDialogController(sorter);

            assertThat(controller.getProfileComboBox().getItems()).hasSize(2);
            assertThat(controller.getProfileComboBox().getValue().getId()).isEqualTo("profile1");
            assertThat(controller.getChannelsListView().getItems()).containsExactly("Das Erste HD", "ZDF HD", "RTL");
            assertThat(controller.getDefaultProfileCheckBox().isSelected()).isTrue();
        });
    }

    @Test
    void testProfileSwitching(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            PreferredChannelOrderSorter sorter = createSampleSorter(tempDir);
            PreferredChannelOrderConfigDialogController controller = new PreferredChannelOrderConfigDialogController(sorter);

            // Add a channel to profile 1
            controller.addChannel("New Channel in Profile 1");
            assertThat(controller.getChannelsListView().getItems()).contains("New Channel in Profile 1");

            // Switch to profile 2
            controller.getProfileComboBox().getSelectionModel().select(1);
            assertThat(controller.getProfileComboBox().getValue().getId()).isEqualTo("profile2");
            assertThat(controller.getChannelsListView().getItems()).containsExactly("SPORT1", "Eurosport 1");
            assertThat(controller.getDefaultProfileCheckBox().isSelected()).isFalse();

            // Switch back to profile 1 and verify the added channel was saved in memory
            controller.getProfileComboBox().getSelectionModel().select(0);
            assertThat(controller.getChannelsListView().getItems()).containsExactly("Das Erste HD", "ZDF HD", "RTL", "New Channel in Profile 1");
            assertThat(controller.getDefaultProfileCheckBox().isSelected()).isTrue();
        });
    }

    @Test
    void testMoveUpAndDown(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            PreferredChannelOrderSorter sorter = createSampleSorter(tempDir);
            PreferredChannelOrderConfigDialogController controller = new PreferredChannelOrderConfigDialogController(sorter);

            // Initially: "Das Erste HD" (0), "ZDF HD" (1), "RTL" (2)
            controller.getChannelsListView().getSelectionModel().select(1); // "ZDF HD"

            controller.moveUp();
            assertThat(controller.getChannelsListView().getItems()).containsExactly("ZDF HD", "Das Erste HD", "RTL");
            assertThat(controller.getChannelsListView().getSelectionModel().getSelectedIndex()).isEqualTo(0);

            // Move down twice
            controller.moveDown();
            assertThat(controller.getChannelsListView().getItems()).containsExactly("Das Erste HD", "ZDF HD", "RTL");
            assertThat(controller.getChannelsListView().getSelectionModel().getSelectedIndex()).isEqualTo(1);

            controller.moveDown();
            assertThat(controller.getChannelsListView().getItems()).containsExactly("Das Erste HD", "RTL", "ZDF HD");
            assertThat(controller.getChannelsListView().getSelectionModel().getSelectedIndex()).isEqualTo(2);
        });
    }

    @Test
    void testAddAndRemoveChannel(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            PreferredChannelOrderSorter sorter = createSampleSorter(tempDir);
            PreferredChannelOrderConfigDialogController controller = new PreferredChannelOrderConfigDialogController(sorter);

            controller.addChannel("ProSieben");
            assertThat(controller.getChannelsListView().getItems()).containsExactly("Das Erste HD", "ZDF HD", "RTL", "ProSieben");

            controller.getChannelsListView().getSelectionModel().select("ZDF HD");
            controller.removeSelectedChannel();
            assertThat(controller.getChannelsListView().getItems()).containsExactly("Das Erste HD", "RTL", "ProSieben");
        });
    }

    @Test
    void testProfileCrud(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            PreferredChannelOrderSorter sorter = createSampleSorter(tempDir);
            PreferredChannelOrderConfigDialogController controller = new PreferredChannelOrderConfigDialogController(sorter);

            // Create new profile
            ChannelProfile newProfile = controller.addNewProfile("Movies Profile");
            assertThat(newProfile.getId()).isEqualTo("movies_profile");
            assertThat(controller.getProfileComboBox().getValue().getName()).isEqualTo("Movies Profile");
            assertThat(controller.getChannelsListView().getItems()).isEmpty();

            controller.addChannel("Sky Cinema");
            assertThat(controller.getChannelsListView().getItems()).containsExactly("Sky Cinema");

            // Rename profile
            controller.renameSelectedProfile("Cinema Movies");
            assertThat(controller.getProfileComboBox().getValue().getName()).isEqualTo("Cinema Movies");

            // Delete profile
            controller.deleteSelectedProfile();
            assertThat(controller.getProfileComboBox().getItems()).hasSize(2);
            assertThat(controller.getProfileComboBox().getItems()).extracting(ChannelProfile::getId)
                    .containsExactly("profile1", "profile2");
        });
    }

    @Test
    void testCreateProfileFromPlaylist(@TempDir Path tempDir) throws Exception {
        Path playlist = tempDir.resolve("my_playlist.m3u");
        Files.writeString(playlist, """
                #EXTM3U
                #EXTINF:-1 tvg-name="ZDF HD",ZDF HD
                http://example.com/zdf
                #EXTINF:-1 tvg-name="Das Erste HD",Das Erste HD
                http://example.com/ard
                #EXTINF:-1 tvg-name="ZDF HD",ZDF HD
                http://example.com/zdf2
                #EXTINF:-1 tvg-name="arte HD",arte HD
                http://example.com/arte
                """);

        runOnFxThread(() -> {
            PreferredChannelOrderSorter sorter = createSampleSorter(tempDir);
            PreferredChannelOrderConfigDialogController controller = new PreferredChannelOrderConfigDialogController(sorter);

            ChannelProfile newProfile;
            try {
                newProfile = controller.addNewProfileFromPlaylist("My Playlist", playlist);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }

            assertThat(newProfile.getId()).isEqualTo("my_playlist");
            assertThat(controller.getProfileComboBox().getValue()).isSameAs(newProfile);
            assertThat(controller.getChannelsListView().getItems())
                    .containsExactly("ZDF HD", "Das Erste HD", "arte HD");
            assertThat(controller.getProfileComboBox().getItems()).hasSize(3);

            controller.saveWorkingState();
            controller.applyAndPersistConfig();
            assertThat(sorter.getSelectedProfileId()).isEqualTo("my_playlist");
            assertThat(sorter.getConfigManager().loadConfig().getProfiles())
                    .extracting(ChannelProfile::getId)
                    .containsExactly("profile1", "profile2", "my_playlist");
        });
    }

    @Test
    void testSaveAndApplyConfig(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            PreferredChannelOrderSorter sorter = createSampleSorter(tempDir);
            PreferredChannelOrderConfigDialogController controller = new PreferredChannelOrderConfigDialogController(sorter);

            // Modify profile 1: move RTL to top
            controller.getChannelsListView().getSelectionModel().select(2); // RTL
            controller.moveUp();
            controller.moveUp();
            assertThat(controller.getChannelsListView().getItems()).containsExactly("RTL", "Das Erste HD", "ZDF HD");

            // Save and apply
            controller.saveWorkingState();
            controller.applyAndPersistConfig();

            assertThat(sorter.getSelectedProfileId()).isEqualTo("profile1");
            assertThat(sorter.getSelectedProfile()).isPresent();
            assertThat(sorter.getSelectedProfile().get().getChannels()).containsExactly("RTL", "Das Erste HD", "ZDF HD");

            // Verify sorting uses the updated order
            Entry e1 = new Entry();
            e1.setName("Das Erste HD");
            Entry e2 = new Entry();
            e2.setName("RTL");
            Entry e3 = new Entry();
            e3.setName("ZDF HD");

            List<Entry> sorted = sorter.sort(List.of(e1, e2, e3));
            assertThat(sorted).extracting(Entry::getName).containsExactly("RTL", "Das Erste HD", "ZDF HD");

            // Verify changes were persisted to manager file
            PreferredChannelOrderConfig loaded = sorter.getConfigManager().loadConfig();
            assertThat(loaded.getProfiles().getFirst().getChannels()).containsExactly("RTL", "Das Erste HD", "ZDF HD");
        });
    }
}

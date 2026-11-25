package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Sorter;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.testsupport.ConfigDirectoryContext;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreferredChannelOrderSorterTest {

    private Entry createEntry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        return entry;
    }

    @Test
    void testSortWithDefaultProfile(@TempDir Path tempDir) {
        PreferredChannelOrderSorter sorter = ConfigDirectoryContext.create(PreferredChannelOrderSorter::new, tempDir);

        Entry e1 = createEntry("RTL");
        Entry e2 = createEntry("Das Erste HD");
        Entry e3 = createEntry("ZDF HD");
        Entry e4 = createEntry("Unknown Channel B");
        Entry e5 = createEntry("Unknown Channel A");

        List<Entry> sorted = sorter.sort(List.of(e1, e4, e2, e5, e3));

        // Default order: Das Erste HD, ZDF HD, ..., RTL
        // Followed by unknown channels sorted alphabetically: Unknown Channel A, Unknown Channel B
        assertThat(sorted).extracting(Entry::getName).containsExactly(
                "Das Erste HD",
                "ZDF HD",
                "RTL",
                "Unknown Channel A",
                "Unknown Channel B"
        );
    }

    @Test
    void testSortWithCustomProfile(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve(PreferredChannelOrderSorter.CONFIG_FILE_NAME);
        YamlConfigManager<PreferredChannelOrderConfig> manager = new YamlConfigManager<>(configFile, PreferredChannelOrderConfig.class);

        ChannelProfile sportProfile = ChannelProfile.builder()
                .id("sport_favoriten")
                .name("Sport Favoriten")
                .channels(List.of("SPORT1", "Eurosport 1", "WELT"))
                .build();

        PreferredChannelOrderConfig config = PreferredChannelOrderConfig.builder()
                .defaultProfile("sport_favoriten")
                .profiles(List.of(sportProfile))
                .build();
        manager.saveConfig(config);

        PreferredChannelOrderSorter sorter = ConfigDirectoryContext.create(PreferredChannelOrderSorter::new, tempDir);
        sorter.setSelectedProfileId("sport_favoriten");

        Entry e1 = createEntry("WELT");
        Entry e2 = createEntry("SPORT1");
        Entry e3 = createEntry("Eurosport 1");
        Entry e4 = createEntry("ARD");

        List<Entry> sorted = sorter.sort(List.of(e1, e4, e2, e3));

        assertThat(sorted).extracting(Entry::getName).containsExactly(
                "SPORT1",
                "Eurosport 1",
                "WELT",
                "ARD"
        );
    }

    @Test
    void testSaveAndLoadInProjectFile(@TempDir Path tempDir) throws Exception {
        PreferredChannelOrderSorter sorter = TestPluginSupport.create(PreferredChannelOrderSorter::new);
        sorter.setSelectedProfileId("sport_favoriten");
        DataModel dataModel = new DataModel("sorter");
        dataModel.getSorters().add(sorter);
        Path projectFile = tempDir.resolve("sorter.m3uproj");

        assertThat(ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel)).isTrue();

        // configManager and config are not part of the project-file
        String yaml = Files.readString(projectFile);
        assertThat(yaml).doesNotContain("configManager");
        assertThat(yaml).doesNotContain("config");
        assertThat(yaml).contains("selectedProfileId: sport_favoriten");

        PreferredChannelOrderSorter loaded = (PreferredChannelOrderSorter) ProjectPersisterUtil.loadProject(projectFile)
                .getSorters().getFirst();
        assertThat(loaded.getSelectedProfileId()).isEqualTo("sport_favoriten");
        assertThat(loaded.getConfig()).isNotNull();
        assertThat(loaded.getSelectedProfile()).isPresent();
        assertThat(loaded.getSelectedProfile().get().getId()).isEqualTo("sport_favoriten");
    }

    @Test
    void testSettingsRoundTrip() {
        PreferredChannelOrderSorter sorter = TestPluginSupport.create(PreferredChannelOrderSorter::new);
        sorter.setSelectedProfileId("sport_favoriten");

        assertThat(sorter.getSettingsType()).isEqualTo(PreferredChannelOrderSorterSettings.class);
        PreferredChannelOrderSorterSettings settings = sorter.getSettings();
        assertThat(settings).isEqualTo(new PreferredChannelOrderSorterSettings("sport_favoriten"));

        PreferredChannelOrderSorter other = TestPluginSupport.create(PreferredChannelOrderSorter::new);
        other.setSettings(settings);
        assertThat(other.getSelectedProfileId()).isEqualTo("sport_favoriten");
        assertThat(other.getSelectedProfile()).map(ChannelProfile::getId).contains("sport_favoriten");
    }

    @Test
    void testMissingSettingsUseTheDefaultProfileOfTheConfiguration(@TempDir Path tempDir) {
        PreferredChannelOrderConfig config = PreferredChannelOrderConfig.builder()
                .defaultProfile("sport_favoriten")
                .profiles(List.of(ChannelProfile.builder().id("sport_favoriten").channels(List.of("SPORT1")).build()))
                .build();
        new YamlConfigManager<>(tempDir.resolve(PreferredChannelOrderSorter.CONFIG_FILE_NAME),
                PreferredChannelOrderConfig.class).saveConfig(config);
        PreferredChannelOrderSorter sorter = ConfigDirectoryContext.create(PreferredChannelOrderSorter::new, tempDir);

        sorter.setSettings(new PreferredChannelOrderSorterSettings(null));

        assertThat(sorter.getSelectedProfileId()).isEqualTo("sport_favoriten");
        assertThat(sorter.getSettings().selectedProfileId()).isEqualTo("sport_favoriten");
    }

    @Test
    void testMissingSettingsWithoutDefaultProfileUseTheStandardProfile(@TempDir Path tempDir) {
        new YamlConfigManager<>(tempDir.resolve(PreferredChannelOrderSorter.CONFIG_FILE_NAME),
                PreferredChannelOrderConfig.class).saveConfig(new PreferredChannelOrderConfig(null, List.of()));
        PreferredChannelOrderSorter sorter = ConfigDirectoryContext.create(PreferredChannelOrderSorter::new, tempDir);

        sorter.setSettings(new PreferredChannelOrderSorterSettings(null));

        assertThat(sorter.getSelectedProfileId()).isEqualTo("deutschland_standard");
    }

    @Test
    void testLoadProjectFileOfPreviousVersion(@TempDir Path tempDir) throws Exception {
        Path projectFile = tempDir.resolve("sorter.m3uproj");
        Files.writeString(projectFile, """
                ---
                name: sorter
                sorters:
                  - type: PreferredChannelOrderSorter
                    enabled: true
                    selectedProfileId: sport_favoriten
                  - type: PreferredChannelOrderSorter
                    enabled: false
                """);

        List<Sorter> sorters = ProjectPersisterUtil.loadProject(projectFile).getSorters();

        assertThat(sorters).hasSize(2).allMatch(PreferredChannelOrderSorter.class::isInstance);
        assertThat(((PreferredChannelOrderSorter) sorters.get(0)).getSelectedProfileId()).isEqualTo("sport_favoriten");
        assertThat(PluginStates.enabled(sorters.get(0)).get()).isTrue();
        // a project-file without selected profile uses the default profile of the configuration
        assertThat(((PreferredChannelOrderSorter) sorters.get(1)).getSelectedProfileId())
                .isEqualTo("deutschland_standard");
        assertThat(PluginStates.enabled(sorters.get(1)).get()).isFalse();
    }
}

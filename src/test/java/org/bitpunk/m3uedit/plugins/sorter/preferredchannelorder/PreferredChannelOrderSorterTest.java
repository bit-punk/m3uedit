package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;
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
        Path configFile = tempDir.resolve("preferred_channel_order.yaml");
        PreferredChannelOrderSorter sorter = new PreferredChannelOrderSorter(configFile);

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
        Path configFile = tempDir.resolve("config.yaml");
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

        PreferredChannelOrderSorter sorter = new PreferredChannelOrderSorter(manager);
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
        PreferredChannelOrderSorter sorter = new PreferredChannelOrderSorter();
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
}

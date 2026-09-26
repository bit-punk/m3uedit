package org.bitpunk.m3uedit.plugins.sorter;

import com.thoughtworks.xstream.XStream;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.sorter.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.model.PreferredChannelOrderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void testSortWithDefaultProfile() {
        PreferredChannelOrderSorter sorter = new PreferredChannelOrderSorter();

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
        PreferredChannelOrderConfigManager manager = new PreferredChannelOrderConfigManager(configFile);

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
    void testXStreamSerializationAndDeserialization() {
        PreferredChannelOrderSorter sorter = new PreferredChannelOrderSorter();
        sorter.setSelectedProfileId("sport_favoriten");

        XStream xstream = new XStream();
        xstream.autodetectAnnotations(true);
        xstream.alias("PreferredChannelOrderSorter", PreferredChannelOrderSorter.class);
        xstream.allowTypesByWildcard(new String[]{
                "javafx.beans.property.*",
                "org.bitpunk.m3uedit.plugins.sorter.*"
        });

        String xml = xstream.toXML(sorter);

        // Verify configManager and config are omitted from XML
        assertThat(xml).doesNotContain("configManager");
        assertThat(xml).doesNotContain("config");
        assertThat(xml).contains("<selectedProfileId>sport_favoriten</selectedProfileId>");

        PreferredChannelOrderSorter deserialized = (PreferredChannelOrderSorter) xstream.fromXML(xml);
        assertThat(deserialized.getSelectedProfileId()).isEqualTo("sport_favoriten");
        assertThat(deserialized.getConfig()).isNotNull();
        assertThat(deserialized.getSelectedProfile()).isPresent();
        assertThat(deserialized.getSelectedProfile().get().getId()).isEqualTo("sport_favoriten");
    }
}

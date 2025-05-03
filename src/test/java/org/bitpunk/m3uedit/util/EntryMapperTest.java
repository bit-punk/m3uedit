package org.bitpunk.m3uedit.util;

import org.bitpunk.m3uedit.builder.Builder;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntryMapperTest {
    @Test
    void testMapEntry() {
        String duration = "123";
        String groupTitle = "ABBA";
        String tvgId = "1122";
        String tvgName = "Arte EPG";
        String tvgLogo = "arte_logo.png";
        String tvgShift = "7";
        String radio = "Arte Radio";
        String channelUri = "https://arte.de/stream/";
        String name = "Arte HD";
        Map<String, String> vlcOpts = Map.of(
                "key1", "value1",
                "key2", "value2",
                "key3", "value2"
        );

        Entry targetEntry = getTargetEntry();
        Entry sourceEntry = getEntry(duration, groupTitle, tvgId, tvgName, tvgLogo, tvgShift, radio, channelUri, name, vlcOpts);
        assertThat(sourceEntry.getName())
                .as("source entry name")
                .isEqualTo(name);

        assertThat(targetEntry)
                .as("target entry before mapping")
                .extracting(Entry::getName, Entry::getVlcOpts)
                .containsExactly("target-entry", null)
                .doesNotContain(sourceEntry.getName(), sourceEntry.getVlcOpts());

        EntryMapper.mapEntry(sourceEntry, targetEntry);
        assertThat(targetEntry)
                .as("target entry after mapping")
                .usingRecursiveComparison()
                .isEqualTo(sourceEntry);
    }

    private Entry getEntry(String duration, String groupTitle, String tvgId, String tvgName, String tvgLogo, String tvgShift, String radio, String channelUri, String name, Map<String, String> vlcOpts) {
        return new Builder()
                .duration(duration)
                .groupTitle(groupTitle)
                .tvgId(tvgId)
                .tvgName(tvgName)
                .tvgLogo(tvgLogo)
                .tvgShift(tvgShift)
                .radio(radio)
                .channelUri(channelUri)
                .channelName(name)
                .vlcOptions(vlcOpts)
                .build();
    }

    private Entry getTargetEntry() {
        Entry sourceEntry = new Entry();
        sourceEntry.setName("target-entry");
        return sourceEntry;
    }
}
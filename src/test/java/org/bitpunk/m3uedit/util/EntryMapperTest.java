package org.bitpunk.m3uedit.util;

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
        assertThat(sourceEntry.getName()).isEqualTo(name);
        assertThat(targetEntry.getName()).isEqualTo("target-entry");
        assertThat(targetEntry.getVlcOpts()).isNotEqualTo(sourceEntry.getVlcOpts());

        EntryMapper.mapEntry(sourceEntry, targetEntry);
        assertThat(targetEntry.getName()).isEqualTo(sourceEntry.getName());
        assertThat(targetEntry.getVlcOpts()).isEqualTo(sourceEntry.getVlcOpts());
        assertThat(targetEntry).isEqualTo(sourceEntry);
    }

    private Entry getEntry(String duration, String groupTitle, String tvgId, String tvgName, String tvgLogo, String tvgShift, String radio, String channelUri, String name, Map<String, String> vlcOPts) {
        Entry sourceEntry = new Entry();
        sourceEntry.setDuration(duration);
        sourceEntry.setGroupTitle(groupTitle);
        sourceEntry.setTvgId(tvgId);
        sourceEntry.setTvgName(tvgName);
        sourceEntry.setTvgLogo(tvgLogo);
        sourceEntry.setTvgShift(tvgShift);
        sourceEntry.setRadio(radio);
        sourceEntry.setChannelUri(channelUri);
        sourceEntry.setName(name);
        sourceEntry.setVlcOpts(vlcOPts);
        return sourceEntry;
    }

    private Entry getTargetEntry() {
        Entry sourceEntry = new Entry();
        sourceEntry.setName("target-entry");
        return sourceEntry;
    }
}
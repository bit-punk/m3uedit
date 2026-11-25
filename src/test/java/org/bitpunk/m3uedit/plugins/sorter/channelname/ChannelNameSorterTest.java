package org.bitpunk.m3uedit.plugins.sorter.channelname;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class ChannelNameSorterTest {

    @Test
    void testSort() {
        List<Entry> entries = getUnsortedList("/test.m3u");
        assertThat(entries).hasSize(3);
        ChannelNameSorter channelNameSorter = TestPluginSupport.create(ChannelNameSorter::new);
        List<Entry> sortedEntries = channelNameSorter.sort(entries);
        assertThat(sortedEntries).hasSize(3);
        assertThat(sortedEntries.get(0).getName()).isEqualTo("La Deux HD");
        assertThat(sortedEntries.get(1).getName()).isEqualTo("La Une HD");
        assertThat(sortedEntries.get(2).getName()).isEqualTo("RTL TVI HD");

    }

    private List<Entry> getUnsortedList(String resourceFilename) {
        return new Parser().parse(ChannelNameSorterTest.class.getResourceAsStream(resourceFilename));
    }
}
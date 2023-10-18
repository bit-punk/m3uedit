package org.bitpunk.m3uedit.plugins.sorter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


class ChannelNameSorterTest {

    @Test
    void testSort() {
        List<Entry> entries = getUnsortedList("/test.m3u");
        assertThat(entries, hasSize(3));
        ChannelNameSorter channelNameSorter = new ChannelNameSorter();
        List<Entry> sortedEntries = channelNameSorter.sort(entries);
        assertThat(sortedEntries, hasSize(3));
        assertThat(sortedEntries.get(0).getChannelName(), is("La Deux HD"));
        assertThat(sortedEntries.get(1).getChannelName(), is("La Une HD"));
        assertThat(sortedEntries.get(2).getChannelName(), is("RTL TVI HD"));

    }

    private List<Entry> getUnsortedList(String resourceFilename) {
        return new Parser().parse(ChannelNameSorterTest.class.getResourceAsStream(resourceFilename));
    }
}
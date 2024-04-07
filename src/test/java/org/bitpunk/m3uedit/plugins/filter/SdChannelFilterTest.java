package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SdChannelFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = new SdChannelFilter();
        List<Entry> entryList = readList("sd_channels.m3u");
        assertThat(entryList).hasSize(9);
        List<Entry> filteredList = filter.filter(entryList);
        assertThat(filteredList).hasSize(5);
        assertThat(filteredList).extracting("name").contains("Das Erste HD");
        assertThat(filteredList).extracting("name").contains("ZDF HD");
        assertThat(filteredList).extracting("name").contains("3sat HD");
        assertThat(filteredList).extracting("name").contains("arte HD");
        assertThat(filteredList).extracting("name").contains("Bingo-TV ᴴᴰ");
    }
}
package org.bitpunk.m3uedit.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.SdChannelFilter;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SdChannelFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = new SdChannelFilter();
        List<Entry> entryList = readList("sd_channels.m3u");
        assertThat(entryList, hasSize(9));
        List<Entry> filteredList = filter.filter(entryList);
        assertThat(filteredList, hasSize(5));
        assertThat(filteredList, hasItem(hasProperty("name", is("Das Erste HD"))));
        assertThat(filteredList, hasItem(hasProperty("name", is("ZDF HD"))));
        assertThat(filteredList, hasItem(hasProperty("name", is("3sat HD"))));
        assertThat(filteredList, hasItem(hasProperty("name", is("arte HD"))));
        assertThat(filteredList, hasItem(hasProperty("name", is("Bingo-TV ᴴᴰ"))));
    }
}
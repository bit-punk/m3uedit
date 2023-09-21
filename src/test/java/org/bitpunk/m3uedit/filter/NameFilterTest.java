package org.bitpunk.m3uedit.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class NameFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = new NameFilter();
        List<Entry> entryList = readList("iptv.m3u");
        assertThat(entryList, hasSize(30));
        List<Entry> filteredList = filter.filter(entryList);
        assertThat(filteredList, hasSize(25));
        assertThat(filteredList, not(hasItem(hasProperty("channelName", matchesPattern(".*QVC.*")))));
        assertThat(filteredList, not(hasItem(hasProperty("channelName", matchesPattern(".*HSE.*")))));

    }

}
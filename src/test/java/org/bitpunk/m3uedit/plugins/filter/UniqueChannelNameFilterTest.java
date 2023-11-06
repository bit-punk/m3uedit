package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class UniqueChannelNameFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = new UniqueNameFilter();
        List<Entry> entryList = readList("duplicate_names.m3u");
        assertThat(entryList, hasSize(6));
        List<Entry> filteredList = filter.filter(entryList);
        assertThat(filteredList, hasSize(4));
    }
}
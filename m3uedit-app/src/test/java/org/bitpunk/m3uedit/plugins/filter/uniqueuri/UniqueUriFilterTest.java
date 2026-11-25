package org.bitpunk.m3uedit.plugins.filter.uniqueuri;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractFilterTest;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueUriFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = TestPluginSupport.create(UniqueUriFilter::new);
        List<Entry> entryList = readList("duplicate_entries.m3u");
        assertThat(entryList).hasSize(5);
        List<Entry> filteredList = filter.filter(entryList);

        assertThat(filteredList).hasSize(4);
    }

}

package org.bitpunk.m3uedit.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class NameFilterTest {

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

    private List<Entry> readList(String resourceName) throws FileNotFoundException {
        List<Entry> entryList;
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource(resourceName)).getFile());
        Parser parser = new Parser();
        entryList = parser.parse(new FileInputStream(file));

        return entryList;
    }
}
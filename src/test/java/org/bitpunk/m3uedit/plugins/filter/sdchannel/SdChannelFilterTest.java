package org.bitpunk.m3uedit.plugins.filter.sdchannel;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractFilterTest;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SdChannelFilterTest extends AbstractFilterTest {

    @Test
    void testFilter() throws FileNotFoundException {
        Filter filter = new SdChannelFilter();
        List<Entry> entryList = readList("sd_channels.m3u");
        List<Entry> filteredList = filter.filter(entryList);
        assertThat(filteredList)
                .hasSize(5)
                .extracting("name")
                .contains("Das Erste HD", "ZDF HD", "3sat HD", "arte HD", "Bingo-TV ᴴᴰ");
    }

}

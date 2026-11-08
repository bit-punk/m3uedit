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

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        return entry;
    }

    private static List<String> filteredNames(String... names) {
        List<Entry> entries = java.util.Arrays.stream(names).map(SdChannelFilterTest::entry).toList();
        return new SdChannelFilter().filter(entries).stream().map(Entry::getName).toList();
    }

    @Test
    void channelsStartingWithTheSameNameAreKept() {
        assertThat(filteredNames("RTL", "RTL ZWEI", "SAT.1", "SAT.1 Gold", "ProSieben", "ProSieben MAXX", "ZDF", "ZDFneo HD"))
                .containsExactly("RTL", "RTL ZWEI", "SAT.1", "SAT.1 Gold", "ProSieben", "ProSieben MAXX", "ZDF", "ZDFneo HD");
    }

    @Test
    void hdSuffixVariantsAreRecognized() {
        assertThat(filteredNames("ZDF", "ZDFHD", "arte", "arte (HD)", "phoenix", "Phoenix hd", "KiKA ", "KiKA HD"))
                .containsExactly("ZDFHD", "arte (HD)", "Phoenix hd", "KiKA HD");
    }

    @Test
    void hdChannelsWithoutSdVariantAndNamesOnlyConsistingOfHdAreKept() {
        assertThat(filteredNames("HD", "ONE HD", "tagesschau24 HD")).containsExactly("HD", "ONE HD", "tagesschau24 HD");
    }

    @Test
    void removalNamesTheHdChannel() {
        Entry sd = entry("RTL");
        List<Entry> input = List.of(sd, entry("RTL ZWEI"), entry("RTL HD"));

        assertThat(new SdChannelFilter().describeRemoval(sd, input)).isEqualTo("\"RTL HD\" exists");
    }
}

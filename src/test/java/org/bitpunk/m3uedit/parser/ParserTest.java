package org.bitpunk.m3uedit.parser;

import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ParserTest {

    private static void assertEntryProperties(int index, String name, String tvgName, String tvgId, String channelUri, String duration, String groupTitle, String radio, String tvgLogo, String tvgShift, List<Entry> entries) {
        assertThat(entries.get(index))
                .hasFieldOrPropertyWithValue("name", name)
                .hasFieldOrPropertyWithValue("tvgName", tvgName)
                .hasFieldOrPropertyWithValue("tvgId", tvgId)
                .hasFieldOrPropertyWithValue("channelUri", channelUri)
                .hasFieldOrPropertyWithValue("duration", duration)
                .hasFieldOrPropertyWithValue("groupTitle", groupTitle)
                .hasFieldOrPropertyWithValue("radio", radio)
                .hasFieldOrPropertyWithValue("tvgLogo", tvgLogo)
                .hasFieldOrPropertyWithValue("tvgShift", tvgShift);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0, La Une HD, La Une HD, La_Une, http://panel.globepremium.com:8000/live/xxx/yyy/84.ts, -1, TV, false, ../logo/La_Une.png, 1",
            "1, La Deux HD, La Deux HD, La_Deux, http://panel.globepremium.com:8000/live/xxx/yyy/85.ts, -1, TV, false, ../logo/La_Deux.png, 2",
            "2, RTL TVI HD, RTL TVI HD, RTL_TVI, http://panel.globepremium.com:8000/live/xxx/yyy/719.ts, -1, TV, false, ../logo/RTL_TVI.png, 3",
    })
    void testParse(int index, String name, String tvgName, String tvgId, String channelUri, String duration, String groupTitle, String radio, String tvgLogo, String tvgShift) {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test.m3u"));

        assertEntryProperties(index, name, tvgName, tvgId, channelUri, duration, groupTitle, radio, tvgLogo, tvgShift, entries);
    }

    @Test
    void testParseVlcOption() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/tvhd_orig.m3u"));
        assertThat(entries).hasSize(30);
        entries.forEach(entry -> assertThat(entry.getVlcOpts()).containsEntry("network-caching", "1000"));
    }

    @Test
    void testParseMultipleVlcOpts() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/tvhd_orig.m3u"));
        assertThat(entries).hasSize(30);
        Entry entry = entries.getFirst();
        assertThat(entry.getName()).isEqualTo("Das Erste HD");
        assertThat(entry.getVlcOpts()).hasSize(3);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0, La Une HD, La Une HD, La_Une, http://panel.globepremium.com:8000/live/xxx/yyy/84.ts, -1, TV, false, ../logo/La_Une.png, 4",
            "1, La Deux HD, La Deux HD, La_Deux, http://panel.globepremium.com:8000/live/xxx/yyy/85.ts, -1, TV, false, ../logo/La_Deux.png, 4",
            "2, RTL TVI HD, RTL TVI HD, RTL_TVI, http://panel.globepremium.com:8000/live/xxx/yyy/719.ts, -1, TV, false, ../logo/RTL_TVI.png, 4"
    })
    void testParseGlobalTvgShift(int index, String name, String tvgName, String tvgId, String channelUri, String duration, String groupTitle, String radio, String tvgLogo, String tvgShift) {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test_global_tvg_shift.m3u"));

        assertEntryProperties(index, name, tvgName, tvgId, channelUri, duration, groupTitle, radio, tvgLogo, tvgShift, entries);
    }

    @Test
    void testParseTvsd() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/tvsd.m3u"));
        assertThat(entries).hasSize(88);
    }

    @Test
    void testParseBigFile() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test_big_file.m3u"));
        assertThat(entries).hasSize(9065);
    }

    @Test
    void testEmptyStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[]{});
        assertThatExceptionOfType(ParsingException.class).isThrownBy(() -> new Parser().parse(byteArrayInputStream));
    }

    @Test
    void testWrongStream() throws IOException {
        InputStream stream = ParserTest.class.getResourceAsStream("/test_global_tvg_shift.m3u");
        assertThat(stream).isNotNull();
        stream.close();
        assertThatExceptionOfType(ParsingException.class).isThrownBy(() -> new Parser().parse(stream));
    }

    private static List<Entry> parseString(String content) {
        return new Parser().parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "#EXTINF:-1 tvg-name=\"Foo\",Foo, Bar                        | Foo, Bar",
            "#EXTINF:-1 tvg-name=\"Foo, Bar\" group-title=\"A,B\",Foo Bar | Foo Bar",
            "#EXTINF:-1, Leading and trailing blanks                       | Leading and trailing blanks",
            "#EXTINF:-1,Sat.1 Gold, HD, Österreich                         | Sat.1 Gold, HD, Österreich",
    })
    void testChannelNameKeepsCommas(String extInf, String expectedName) {
        List<Entry> entries = parseString("#EXTM3U\n" + extInf + "\nhttp://example.com/stream.ts\n");
        assertThat(entries).singleElement().extracting(Entry::getName).isEqualTo(expectedName);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "#EXTINF:-1 tvg-name=\"Foo\"",
            "#EXTINF:-1 tvg-name=\"Foo\",",
            "#EXTINF:-1 tvg-name=\"Foo, Bar\",   ",
    })
    void testMissingChannelNameIsNull(String extInf) {
        List<Entry> entries = parseString("#EXTM3U\n" + extInf + "\nhttp://example.com/stream.ts\n");
        assertThat(entries).singleElement().extracting(Entry::getName).isNull();
    }

    @Test
    void testBlankLinesAndUnsupportedDirectivesAreIgnored() {
        List<Entry> entries = parseString("""
                #EXTM3U

                # a plain comment
                #EXTINF:-1,First
                #EXTGRP:News

                http://example.com/first.ts

                #EXTINF:-1,Second
                #EXTVLCOPT:network-caching=1000
                http://example.com/second.ts   \r
                """);

        assertThat(entries).extracting(Entry::getName).containsExactly("First", "Second");
        assertThat(entries).extracting(Entry::getChannelUri)
                .containsExactly("http://example.com/first.ts", "http://example.com/second.ts");
        assertThat(entries.get(1).getVlcOpts()).containsEntry("network-caching", "1000");
    }

    @Test
    void testUriWithoutPrecedingExtInfIsRejected() {
        String content = """
                #EXTM3U
                #EXTINF:-1,First
                http://example.com/first.ts
                http://example.com/orphan.ts
                """;
        assertThatExceptionOfType(ParsingException.class).isThrownBy(() -> parseString(content));
    }

    @Test
    void testEntriesDoNotShareVlcOptions() {
        List<Entry> entries = parseString("""
                #EXTM3U
                #EXTINF:-1,First
                #EXTVLCOPT:network-caching=1000
                http://example.com/first.ts
                #EXTINF:-1,Second
                http://example.com/second.ts
                """);

        assertThat(entries.get(0).getVlcOpts()).containsOnlyKeys("network-caching");
        assertThat(entries.get(1).getVlcOpts()).isEmpty();
    }
}

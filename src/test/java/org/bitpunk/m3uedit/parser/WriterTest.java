package org.bitpunk.m3uedit.parser;

import org.bitpunk.m3uedit.builder.Builder;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WriterTest {

    @Test
    void testWrite() {
        String testFilename = "/tvhd_orig.m3u";
        String referenceFilename = "/tvhd_reference.m3u";
        List<Entry> entries = new Parser().parse(WriterTest.class.getResourceAsStream(testFilename));

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        new Writer().write(entries, byteArrayOutputStream);
        assertThat(byteArrayOutputStream.toString()).hasToString(getOrigFile(referenceFilename));
    }

    @Test
    void testWriteSingleQuote() {
        Entry testEntry = new Builder()
                .channelUri("http://test.com")
                .tvgName("testing")
                .channelName("test-channel'name")
                .groupTitle("TV'Group")
                .duration("-1")
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Writer().write(Collections.singletonList(testEntry), baos);
        assertThat(baos.toString()).hasToString("#EXTM3U" + System.lineSeparator() +
                "#EXTINF:-1 tvg-name=\"testing\" group-title=\"TV'Group\",test-channel'name" + System.lineSeparator() +
                "http://test.com");
    }

    private static String write(List<Entry> entries) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new Writer().write(entries, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private static List<Entry> parse(String content) {
        return new Parser().parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/test.m3u", "/tvhd_orig.m3u", "/tvsd_orig.m3u", "/test_global_tvg_shift.m3u",
            "/duplicate_names.m3u", "/tvhd.m3u", "/test_big_file.m3u"})
    void testRoundTripKeepsAllEntries(String resource) {
        List<Entry> original = new Parser().parse(WriterTest.class.getResourceAsStream(resource));

        List<Entry> reparsed = parse(write(original));

        assertThat(reparsed).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(original);
    }

    @Test
    void testEntryWithoutDurationAndAttributesIsStillValid() {
        Entry entry = new Builder().channelName("Plain").channelUri("http://example.com/plain.ts").build();
        entry.setVlcOpts(null);    // e.g. entries created in the entry-dialog

        String written = write(List.of(entry));

        assertThat(written).isEqualTo("#EXTM3U" + System.lineSeparator()
                + "#EXTINF:-1,Plain" + System.lineSeparator()
                + "http://example.com/plain.ts");
        assertThat(parse(written)).singleElement()
                .satisfies(parsed -> assertThat(parsed.getName()).isEqualTo("Plain"))
                .satisfies(parsed -> assertThat(parsed.getDuration()).isEqualTo("-1"));
    }

    @Test
    void testDoubleQuotesAndLineBreaksDoNotBreakThePlaylist() {
        Entry entry = new Builder()
                .channelName("Name with\nline break")
                .tvgName("The \"best\" channel")
                .groupTitle("News\r\nWorld")
                .channelUri("http://example.com/a.ts")
                .duration("-1")
                .build();

        List<Entry> reparsed = parse(write(List.of(entry)));

        assertThat(reparsed).singleElement().satisfies(parsed -> {
            assertThat(parsed.getName()).isEqualTo("Name with line break");
            assertThat(parsed.getTvgName()).isEqualTo("The 'best' channel");
            assertThat(parsed.getGroupTitle()).isEqualTo("News World");
            assertThat(parsed.getChannelUri()).isEqualTo("http://example.com/a.ts");
        });
    }

    @Test
    void testEpgUrlsAreWrittenToTheHeader() {
        Entry entry = new Builder().channelName("ZDF").tvgId("ZDF.de").channelUri("http://example.com/zdf").build();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        new Writer().write(List.of(entry), List.of("http://epg/a.xml.gz", " http://epg/b.xml ", "http://epg/a.xml.gz"),
                outputStream);

        String written = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(written).startsWith("#EXTM3U x-tvg-url=\"http://epg/a.xml.gz,http://epg/b.xml\" "
                + "url-tvg=\"http://epg/a.xml.gz,http://epg/b.xml\"" + System.lineSeparator());
        assertThat(new Parser().parse(new ByteArrayInputStream(outputStream.toByteArray())).getEpgUrls())
                .containsExactly("http://epg/a.xml.gz", "http://epg/b.xml");
    }

    @Test
    void testNoEpgUrlsWriteAPlainHeader() {
        assertThat(Writer.header(Writer.epgAttributes(List.of()))).isEqualTo("#EXTM3U");
        assertThat(Writer.header(Writer.epgAttributes(null))).isEqualTo("#EXTM3U");
        assertThat(Writer.header(Writer.epgAttributes(java.util.Arrays.asList(" ", null)))).isEqualTo("#EXTM3U");
    }

    @Test
    void testCommaInNameAndUtf8AreKept() {
        Entry entry = new Builder()
                .channelName("ARD, Das Erste – Österreich")
                .groupTitle("Öffentlich-rechtlich")
                .channelUri("http://example.com/ä.ts")
                .duration("0")
                .build();

        assertThat(parse(write(List.of(entry)))).usingRecursiveFieldByFieldElementComparator().containsExactly(entry);
    }

    private String getOrigFile(String fileName) {
        return new BufferedReader(new InputStreamReader(Objects.requireNonNull(WriterTest.class.getResourceAsStream(fileName))))
                .lines()
                .collect(Collectors.joining(System.lineSeparator()));
    }

    @Test
    void testHeaderAttributesAreWrittenInTheirOrder() {
        java.util.Map<String, String> attributes = new java.util.LinkedHashMap<>();
        attributes.put("url-tvg", "http://epg/a.xml");
        attributes.put("x-tvg-url", "http://epg/\"b\".xml");
        attributes.put("empty", " ");

        assertThat(Writer.header(attributes)).isEqualTo("#EXTM3U url-tvg=\"http://epg/a.xml\" x-tvg-url=\"http://epg/'b'.xml\"");
    }
}

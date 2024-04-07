package org.bitpunk.m3uedit.parser;

import org.bitpunk.m3uedit.builder.Builder;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WriterTest {

    @Test
    void testWrite() {
        String testFilename = "/tvhd_orig.m3u";
        String referenceFilename = "/tvhd_reference.m3u";
        List<Entry> entries = new Parser().parse(WriterTest.class.getResourceAsStream(testFilename));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Writer().write(entries, baos);
        assertThat(baos.toString()).isEqualTo(getOrigFile(referenceFilename));
    }

    @Test
    void testWriteSingleQuote() {
        Entry entry = new Builder()
                .channelUri("http://test.com")
                .tvgName("testing")
                .channelName("test-channel'name")
                .groupTitle("TV'Group")
                .duration("-1")
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Writer().write(Collections.singletonList(entry), baos);
        assertThat(baos.toString()).isEqualTo("#EXTM3U" + System.lineSeparator() +
                "#EXTINF:-1 tvg-name=\"testing\" group-title=\"TV'Group\",test-channel'name" + System.lineSeparator() +
                "http://test.com");
    }

    private String getOrigFile(String fileName) {
        return new BufferedReader(new InputStreamReader(WriterTest.class.getResourceAsStream(fileName)))
                .lines()
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
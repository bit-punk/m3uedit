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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WriterTest {

    @Test
    void testWrite() {
        List<Entry> entries = new Parser().parse(WriterTest.class.getResourceAsStream("/test.m3u"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Writer().write(entries, baos);
        assertThat(baos.toString(), is(getOrigFile()));
    }

    @Test
    void testWriteSingleQuote() {
        Entry entry = new Builder()
                .channelUri("http://test.com")
                .tvgName("test'ing")
                .channelName("test-channel'name")
                .groupTitle("TV'Group")
                .duration("-1")
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Writer().write(Collections.singletonList(entry), baos);
        assertThat(baos.toString(), is("#EXTM3U" + System.lineSeparator() +
                "#EXTINF:-1 tvg-name=\"test'ing\" group-title=\"TV'Group\",test-channel'name" + System.lineSeparator() +
                "http://test.com"));
    }

    private String getOrigFile() {
        return new BufferedReader(new InputStreamReader(WriterTest.class.getResourceAsStream("/test.m3u")))
                .lines()
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
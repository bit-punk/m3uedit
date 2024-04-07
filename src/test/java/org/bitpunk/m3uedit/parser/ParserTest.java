package org.bitpunk.m3uedit.parser;


import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ParserTest {

    @Test
    void testParse() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test.m3u"));

        assertThat(entries).hasSize(3);

        assertThat(entries.get(0).getName()).isEqualTo("La Une HD");
        assertThat(entries.get(0).getTvgName()).isEqualTo("La Une HD");
        assertThat(entries.get(0).getTvgId()).isEqualTo("La_Une");
        assertThat(entries.get(0).getChannelUri()).isEqualTo("http://panel.globepremium.com:8000/live/xxx/yyy/84.ts");
        assertThat(entries.get(0).getDuration()).isEqualTo("-1");
        assertThat(entries.get(0).getGroupTitle()).isEqualTo("TV");
        assertThat(entries.get(0).getName()).isEqualTo("La Une HD");
        assertThat(entries.get(0).getRadio()).isEqualTo("false");
        assertThat(entries.get(0).getTvgLogo()).isEqualTo("../logo/La_Une.png");
        assertThat(entries.get(0).getTvgShift()).isEqualTo("1");

        assertThat(entries.get(1).getName()).isEqualTo("La Deux HD");
        assertThat(entries.get(1).getTvgName()).isEqualTo("La Deux HD");
        assertThat(entries.get(1).getTvgId()).isEqualTo("La_Deux");
        assertThat(entries.get(1).getChannelUri()).isEqualTo("http://panel.globepremium.com:8000/live/xxx/yyy/85.ts");
        assertThat(entries.get(1).getDuration()).isEqualTo("-1");
        assertThat(entries.get(1).getGroupTitle()).isEqualTo("TV");
        assertThat(entries.get(1).getName()).isEqualTo("La Deux HD");
        assertThat(entries.get(1).getRadio()).isEqualTo("false");
        assertThat(entries.get(1).getTvgLogo()).isEqualTo("../logo/La_Deux.png");
        assertThat(entries.get(1).getTvgShift()).isEqualTo("2");

        assertThat(entries.get(2).getName()).isEqualTo("RTL TVI HD");
        assertThat(entries.get(2).getTvgName()).isEqualTo("RTL TVI HD");
        assertThat(entries.get(2).getTvgId()).isEqualTo("RTL_TVI");
        assertThat(entries.get(2).getChannelUri()).isEqualTo("http://panel.globepremium.com:8000/live/xxx/yyy/719.ts");
        assertThat(entries.get(2).getDuration()).isEqualTo("-1");
        assertThat(entries.get(2).getGroupTitle()).isEqualTo("TV");
        assertThat(entries.get(2).getName()).isEqualTo("RTL TVI HD");
        assertThat(entries.get(2).getRadio()).isEqualTo("false");
        assertThat(entries.get(2).getTvgLogo()).isEqualTo("../logo/RTL_TVI.png");
        assertThat(entries.get(2).getTvgShift()).isEqualTo("3");
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
        Entry entry = entries.get(0);
        assertThat(entry.getName()).isEqualTo("Das Erste HD");
        assertThat(entry.getVlcOpts()).hasSize(3);
    }

    @Test
    void testParseGlobalTvgShift() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test_global_tvg_shift.m3u"));

        assertThat(entries).hasSize(3);

        assertThat(entries.get(0).getName()).isEqualTo("La Une HD");
        assertThat(entries.get(0).getTvgName()).isEqualTo("La Une HD");
        assertThat(entries.get(0).getTvgId()).isEqualTo("La_Une");
        assertThat(entries.get(0).getChannelUri()).isEqualTo("http://panel.globepremium.com:8000/live/xxx/yyy/84.ts");
        assertThat(entries.get(0).getDuration()).isEqualTo("-1");
        assertThat(entries.get(0).getGroupTitle()).isEqualTo("TV");
        assertThat(entries.get(0).getName()).isEqualTo("La Une HD");
        assertThat(entries.get(0).getRadio()).isEqualTo("false");
        assertThat(entries.get(0).getTvgLogo()).isEqualTo("../logo/La_Une.png");
        assertThat(entries.get(0).getTvgShift()).isEqualTo("4");

        assertThat(entries.get(1).getName()).isEqualTo("La Deux HD");
        assertThat(entries.get(1).getTvgName()).isEqualTo("La Deux HD");
        assertThat(entries.get(1).getTvgId()).isEqualTo("La_Deux");
        assertThat(entries.get(1).getChannelUri()).isEqualTo("http://panel.globepremium.com:8000/live/xxx/yyy/85.ts");
        assertThat(entries.get(1).getDuration()).isEqualTo("-1");
        assertThat(entries.get(1).getGroupTitle()).isEqualTo("TV");
        assertThat(entries.get(1).getName()).isEqualTo("La Deux HD");
        assertThat(entries.get(1).getRadio()).isEqualTo("false");
        assertThat(entries.get(1).getTvgLogo()).isEqualTo("../logo/La_Deux.png");
        assertThat(entries.get(1).getTvgShift()).isEqualTo("4");

        assertThat(entries.get(2).getName()).isEqualTo("RTL TVI HD");
        assertThat(entries.get(2).getTvgName()).isEqualTo("RTL TVI HD");
        assertThat(entries.get(2).getTvgId()).isEqualTo("RTL_TVI");
        assertThat(entries.get(2).getChannelUri()).isEqualTo("http://panel.globepremium.com:8000/live/xxx/yyy/719.ts");
        assertThat(entries.get(2).getDuration()).isEqualTo("-1");
        assertThat(entries.get(2).getGroupTitle()).isEqualTo("TV");
        assertThat(entries.get(2).getName()).isEqualTo("RTL TVI HD");
        assertThat(entries.get(2).getRadio()).isEqualTo("false");
        assertThat(entries.get(2).getTvgLogo()).isEqualTo("../logo/RTL_TVI.png");
        assertThat(entries.get(2).getTvgShift()).isEqualTo("4");
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
}


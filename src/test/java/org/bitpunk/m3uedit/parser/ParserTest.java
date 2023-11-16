package org.bitpunk.m3uedit.parser;


import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ParserTest {

    @Test
    void testParse() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test.m3u"));

        assertThat(entries, hasSize(3));

        assertThat(entries.get(0).getName(), is("La Une HD"));
        assertThat(entries.get(0).getTvgName(), is("La Une HD"));
        assertThat(entries.get(0).getTvgId(), is("La_Une"));
        assertThat(entries.get(0).getChannelUri(), is("http://panel.globepremium.com:8000/live/xxx/yyy/84.ts"));
        assertThat(entries.get(0).getDuration(), is("-1"));
        assertThat(entries.get(0).getGroupTitle(), is("TV"));
        assertThat(entries.get(0).getName(), is("La Une HD"));
        assertThat(entries.get(0).getRadio(), is("false"));
        assertThat(entries.get(0).getTvgLogo(), is("../logo/La_Une.png"));
        assertThat(entries.get(0).getTvgShift(), is("1"));

        assertThat(entries.get(1).getName(), is("La Deux HD"));
        assertThat(entries.get(1).getTvgName(), is("La Deux HD"));
        assertThat(entries.get(1).getTvgId(), is("La_Deux"));
        assertThat(entries.get(1).getChannelUri(), is("http://panel.globepremium.com:8000/live/xxx/yyy/85.ts"));
        assertThat(entries.get(1).getDuration(), is("-1"));
        assertThat(entries.get(1).getGroupTitle(), is("TV"));
        assertThat(entries.get(1).getName(), is("La Deux HD"));
        assertThat(entries.get(1).getRadio(), is("false"));
        assertThat(entries.get(1).getTvgLogo(), is("../logo/La_Deux.png"));
        assertThat(entries.get(1).getTvgShift(), is("2"));

        assertThat(entries.get(2).getName(), is("RTL TVI HD"));
        assertThat(entries.get(2).getTvgName(), is("RTL TVI HD"));
        assertThat(entries.get(2).getTvgId(), is("RTL_TVI"));
        assertThat(entries.get(2).getChannelUri(), is("http://panel.globepremium.com:8000/live/xxx/yyy/719.ts"));
        assertThat(entries.get(2).getDuration(), is("-1"));
        assertThat(entries.get(2).getGroupTitle(), is("TV"));
        assertThat(entries.get(2).getName(), is("RTL TVI HD"));
        assertThat(entries.get(2).getRadio(), is("false"));
        assertThat(entries.get(2).getTvgLogo(), is("../logo/RTL_TVI.png"));
        assertThat(entries.get(2).getTvgShift(), is("3"));
    }

    @Test
    void testParseVlcOption() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/tvhd_orig.m3u"));
        assertThat(entries, hasSize(30));
        assertThat(entries, everyItem(hasProperty("vlcOpts", hasEntry("network-caching", "1000"))));
    }

    @Test
    void testParseMultipleVlcOpts() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/tvhd_orig.m3u"));
        assertThat(entries, hasSize(30));
        Entry entry = entries.get(0);
        assertThat(entry.getName(), is("Das Erste HD"));
        assertThat(entry.getVlcOpts(), aMapWithSize(3));
    }

    @Test
    void testParseGlobalTvgShift() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test_global_tvg_shift.m3u"));

        assertThat(entries, hasSize(3));

        assertThat(entries.get(0).getName(), is("La Une HD"));
        assertThat(entries.get(0).getTvgName(), is("La Une HD"));
        assertThat(entries.get(0).getTvgId(), is("La_Une"));
        assertThat(entries.get(0).getChannelUri(), is("http://panel.globepremium.com:8000/live/xxx/yyy/84.ts"));
        assertThat(entries.get(0).getDuration(), is("-1"));
        assertThat(entries.get(0).getGroupTitle(), is("TV"));
        assertThat(entries.get(0).getName(), is("La Une HD"));
        assertThat(entries.get(0).getRadio(), is("false"));
        assertThat(entries.get(0).getTvgLogo(), is("../logo/La_Une.png"));
        assertThat(entries.get(0).getTvgShift(), is("4"));

        assertThat(entries.get(1).getName(), is("La Deux HD"));
        assertThat(entries.get(1).getTvgName(), is("La Deux HD"));
        assertThat(entries.get(1).getTvgId(), is("La_Deux"));
        assertThat(entries.get(1).getChannelUri(), is("http://panel.globepremium.com:8000/live/xxx/yyy/85.ts"));
        assertThat(entries.get(1).getDuration(), is("-1"));
        assertThat(entries.get(1).getGroupTitle(), is("TV"));
        assertThat(entries.get(1).getName(), is("La Deux HD"));
        assertThat(entries.get(1).getRadio(), is("false"));
        assertThat(entries.get(1).getTvgLogo(), is("../logo/La_Deux.png"));
        assertThat(entries.get(1).getTvgShift(), is("4"));

        assertThat(entries.get(2).getName(), is("RTL TVI HD"));
        assertThat(entries.get(2).getTvgName(), is("RTL TVI HD"));
        assertThat(entries.get(2).getTvgId(), is("RTL_TVI"));
        assertThat(entries.get(2).getChannelUri(), is("http://panel.globepremium.com:8000/live/xxx/yyy/719.ts"));
        assertThat(entries.get(2).getDuration(), is("-1"));
        assertThat(entries.get(2).getGroupTitle(), is("TV"));
        assertThat(entries.get(2).getName(), is("RTL TVI HD"));
        assertThat(entries.get(2).getRadio(), is("false"));
        assertThat(entries.get(2).getTvgLogo(), is("../logo/RTL_TVI.png"));
        assertThat(entries.get(2).getTvgShift(), is("4"));
    }

     @Test
    void testParseTvsd() {
         List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/tvsd.m3u"));
         assertThat(entries, hasSize(88));
     }

    @Test
    void testParseBigFile() {
        List<Entry> entries = new Parser().parse(ParserTest.class.getResourceAsStream("/test_big_file.m3u"));
        assertThat(entries, hasSize(9065));
    }

    @Test
    void testEmptyStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[]{});
        Assertions.assertThrows(ParsingException.class, () -> new Parser().parse(byteArrayInputStream));
    }

    @Test
    void testWrongStream() throws IOException {
        InputStream stream = ParserTest.class.getResourceAsStream("/test_global_tvg_shift.m3u");
        assertThat(stream, is(notNullValue()));
        stream.close();
        Assertions.assertThrows(ParsingException.class, () -> new Parser().parse(stream));
    }
}


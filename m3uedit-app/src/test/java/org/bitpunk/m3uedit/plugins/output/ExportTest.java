package org.bitpunk.m3uedit.plugins.output;

import org.bitpunk.m3uedit.api.Export.ExportFile;
import org.bitpunk.m3uedit.api.Export;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ExportTest {

    private static ExportFile file(String suffix, String... attributes) {
        return new ExportFile("file " + suffix, suffix, List.of(attributes), Path.of("unused"));
    }

    @Test
    void testFilesAreNamedAfterThePlaylist() {
        ExportFile guide = file(".xml.gz");

        assertThat(guide.fileNameFor("tv.m3u")).isEqualTo("tv.xml.gz");
        assertThat(guide.fileNameFor("tv.hd.m3u8")).isEqualTo("tv.hd.xml.gz");
        assertThat(guide.fileNameFor("playlist")).isEqualTo("playlist.xml.gz");
        assertThat(guide.fileNameFor(".hidden")).isEqualTo(".hidden.xml.gz");
        assertThat(guide.fileNameFor(null)).isEqualTo("playlist.xml.gz");
    }

    @Test
    void testFilesWithUrlReplaceTheEpgUrls() {
        ExportFile guide = file(".xml.gz", "x-tvg-url", "url-tvg");
        Export export = new Export(List.of(), List.of("http://source/epg.xml"), List.of(guide));

        assertThat(export.headerAttributes(file -> "http://nas/tv.xml.gz")).containsExactly(
                entry("x-tvg-url", "http://nas/tv.xml.gz"), entry("url-tvg", "http://nas/tv.xml.gz"));
    }

    @Test
    void testFilesWithoutUrlKeepTheEpgUrls() {
        ExportFile guide = file(".xml.gz", "x-tvg-url", "url-tvg");
        Export export = new Export(List.of(), List.of("http://source/epg.xml"), List.of(guide));

        assertThat(export.headerAttributes(file -> null)).containsExactly(
                entry("x-tvg-url", "http://source/epg.xml"), entry("url-tvg", "http://source/epg.xml"));
    }

    @Test
    void testSeveralFilesWithTheSameAttributeAreJoined() {
        ExportFile first = file(".a.xml", "x-tvg-url");
        ExportFile second = file(".b.xml", "x-tvg-url");
        Export export = new Export(List.of(), List.of(), List.of(first, second));

        assertThat(export.headerAttributes(file -> "http://nas/" + file.suffix())).containsExactly(
                entry("x-tvg-url", "http://nas/.a.xml,http://nas/.b.xml"));
    }

    @Test
    void testNullValuesAreEmpty() {
        Export export = new Export(null, null, null);

        assertThat(export.playlist()).isEmpty();
        assertThat(export.headerAttributes(file -> null)).isEmpty();
    }
}

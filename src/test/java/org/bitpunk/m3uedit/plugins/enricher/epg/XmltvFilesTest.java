package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class XmltvFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void testChannelsAreRead() throws IOException {
        List<XmltvChannel> channels = XmltvFiles.readChannels(XmltvTestFiles.write(tempDir.resolve("guide.xml"),
                XmltvTestFiles.GUIDE));

        assertThat(channels).containsExactly(
                new XmltvChannel("ZDF.de", List.of("ZDF", "ZDF HD")),
                new XmltvChannel("DasErste.de", List.of("Das Erste")),
                new XmltvChannel("arte.de", List.of("arte")),
                new XmltvChannel("Late.de", List.of("Late Channel")));
    }

    @Test
    void testGzipCompressedFilesAreRead() throws IOException {
        Path file = XmltvTestFiles.writeGzip(tempDir.resolve("guide.xml.gz"), XmltvTestFiles.GUIDE);

        assertThat(XmltvFiles.readChannels(file)).hasSize(4);
    }

    @Test
    void testInvalidFilesAreReported() throws IOException {
        Path file = XmltvTestFiles.write(tempDir.resolve("page.html"), "<html><body>Not found</body>");

        assertThatIOException().isThrownBy(() -> XmltvFiles.readChannels(file)).withMessageContaining("No valid XMLTV file");
    }

    @Test
    void testExternalEntitiesAreNotResolved() throws IOException {
        Path secret = XmltvTestFiles.write(tempDir.resolve("secret.txt"), "secret");
        Path file = XmltvTestFiles.write(tempDir.resolve("evil.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE tv [<!ENTITY xxe SYSTEM "%s">]>
                <tv><channel id="a"><display-name>&xxe;</display-name></channel></tv>
                """.formatted(secret.toUri()));

        try {
            assertThat(XmltvFiles.readChannels(file)).allSatisfy(channel ->
                    assertThat(channel.displayNames()).doesNotContain("secret"));
        } catch (IOException e) {
            // rejecting the file is fine as well
            assertThat(e).hasMessageContaining("No valid XMLTV file");
        }
    }

    @Test
    void testProgrammesOfTheSelectedChannelsAreRead() throws IOException {
        Path file = XmltvTestFiles.write(tempDir.resolve("programmes.xml"), """
                <tv>
                  <channel id="A"><display-name>A</display-name></channel>
                  <programme start="20261113200000 +0100" stop="20261113201500 +0100" channel="A">
                    <title lang="de">Tagesschau</title><title lang="en">News</title>
                    <sub-title>20 Uhr</sub-title>
                    <desc>Nachrichten &amp; <b>Wetter</b></desc>
                    <credits><actor>Somebody</actor></credits>
                    <category>News</category><category>Info</category>
                  </programme>
                  <programme start="20261113201500 +0100" channel="A"><title>Tatort</title><desc>Teil 1.\\n\\nTeil 2.</desc></programme>
                  <programme start="20261113200000 +0100" channel="B"><title>Other</title></programme>
                </tv>
                """);

        List<org.bitpunk.m3uedit.plugins.enricher.Programme> programmes = XmltvFiles.readProgrammes(file,
                java.util.Map.of("A", "a.de"), java.util.Map.of("a.de", "Channel A"), java.time.ZoneOffset.UTC);

        assertThat(programmes).containsExactly(
                new org.bitpunk.m3uedit.plugins.enricher.Programme("a.de", "Channel A",
                        java.time.Instant.parse("2026-11-13T19:00:00Z"), java.time.Instant.parse("2026-11-13T19:15:00Z"),
                        "Tagesschau", "20 Uhr", "Nachrichten & Wetter", "News"),
                new org.bitpunk.m3uedit.plugins.enricher.Programme("a.de", "Channel A",
                        java.time.Instant.parse("2026-11-13T19:15:00Z"), null, "Tatort", null, "Teil 1.\n\nTeil 2.", null));
    }
}

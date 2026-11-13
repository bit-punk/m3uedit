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
}

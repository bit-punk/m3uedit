package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Small XMLTV files for tests.
 */
final class XmltvTestFiles {
    /**
     * The context of the enricher, providing its texts.
     */
    static final PluginContext CONTEXT = new DefaultPluginContext(EpgEnricher.class);
    static final String GUIDE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd">
            <tv generator-info-name="test">
              <channel id="ZDF.de">
                <display-name lang="de">ZDF</display-name>
                <display-name>ZDF HD</display-name>
                <icon src="http://example.com/zdf.png"/>
              </channel>
              <channel id="DasErste.de"><display-name>Das Erste</display-name></channel>
              <channel id="arte.de"><display-name>arte</display-name></channel>
              <programme start="20261113200000 +0100" stop="20261113201500 +0100" channel="DasErste.de">
                <title lang="de">Tagesschau</title>
                <desc>Nachrichten &amp; Wetter</desc>
              </programme>
              <channel id="Late.de"><display-name>Late Channel</display-name></channel>
            </tv>
            """;

    private XmltvTestFiles() {
    }

    static Path write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    static Path writeGzip(Path file, String content) throws IOException {
        try (OutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(file))) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    static String guide(String... channelIds) {
        StringBuilder guide = new StringBuilder("<?xml version=\"1.0\"?>\n<tv>\n");
        for (String id : channelIds) {
            guide.append("  <channel id=\"").append(id).append("\"><display-name>").append(id.replaceAll("\\..*", ""))
                    .append("</display-name></channel>\n");
        }
        return guide.append("</tv>\n").toString();
    }
}

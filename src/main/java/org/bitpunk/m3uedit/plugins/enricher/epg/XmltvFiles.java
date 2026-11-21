package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.plugins.enricher.Programme;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Reads XMLTV files (plain or gzip-compressed), see <a href="https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd">xmltv.dtd</a>.
 */
final class XmltvFiles {

    private XmltvFiles() {
    }

    /**
     * @return a stream of the uncompressed content of the file, whether it is gzip-compressed or not
     */
    static InputStream open(Path file) throws IOException {
        InputStream inputStream = new BufferedInputStream(Files.newInputStream(file), 64 * 1024);
        try {
            inputStream.mark(2);
            int first = inputStream.read();
            int second = inputStream.read();
            inputStream.reset();
            if (first == 0x1f && second == 0x8b) {
                return new BufferedInputStream(new GZIPInputStream(inputStream, 64 * 1024), 64 * 1024);
            }
            return inputStream;
        } catch (IOException e) {
            inputStream.close();
            throw e;
        }
    }

    /**
     * Creates a reader not resolving external entities or DTDs: the files are downloaded from the internet.
     */
    static XMLStreamReader createReader(InputStream inputStream) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory.createXMLStreamReader(inputStream);
    }

    /**
     * Reads the channels of an XMLTV file; the programmes are skipped.
     *
     * @throws IOException if the file cannot be read or is no valid XML
     */
    static List<XmltvChannel> readChannels(Path file) throws IOException {
        List<XmltvChannel> channels = new ArrayList<>();
        try (InputStream inputStream = open(file)) {
            XMLStreamReader reader = createReader(inputStream);
            try {
                String channelId = null;
                List<String> displayNames = new ArrayList<>();
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String element = reader.getLocalName();
                        if ("channel".equals(element)) {
                            channelId = reader.getAttributeValue(null, "id");
                            displayNames = new ArrayList<>();
                        } else if ("display-name".equals(element) && channelId != null) {
                            String displayName = reader.getElementText().strip();
                            if (!displayName.isEmpty()) {
                                displayNames.add(displayName);
                            }
                        } else if ("programme".equals(element)) {
                            skipElement(reader);
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT && "channel".equals(reader.getLocalName())) {
                        if (channelId != null && !channelId.isBlank()) {
                            channels.add(new XmltvChannel(channelId.strip(), displayNames));
                        }
                        channelId = null;
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException(Messages.get("epgEnricher.xmltv.invalid", e.getMessage()), e);
        }
        return channels;
    }

    /**
     * Reads the programmes of the given channels of an XMLTV file.
     *
     * @param channels the ids of the channels in the file, mapped to the channel id of the programmes read
     * @param names    the name of each channel, by the channel id of the programmes read
     */
    static List<Programme> readProgrammes(Path file, Map<String, String> channels, Map<String, String> names,
                                          ZoneId defaultZone) throws IOException {
        List<Programme> programmes = new ArrayList<>();
        try (InputStream inputStream = open(file)) {
            XMLStreamReader reader = createReader(inputStream);
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT || !"programme".equals(reader.getLocalName())) {
                        continue;
                    }
                    String channelId = channels.get(reader.getAttributeValue(null, "channel"));
                    if (channelId == null) {
                        skipElement(reader);
                    } else {
                        programmes.add(readProgramme(reader, channelId, names.get(channelId), defaultZone));
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException(Messages.get("epgEnricher.xmltv.invalid", e.getMessage()), e);
        }
        return programmes;
    }

    /**
     * Reads the programme the reader is positioned at; only the first title, sub-title, description and category
     * are taken.
     */
    private static Programme readProgramme(XMLStreamReader reader, String channelId, String channelName,
                                           ZoneId defaultZone) throws XMLStreamException {
        Instant start = XmltvTimes.parse(reader.getAttributeValue(null, "start"), defaultZone);
        Instant stop = XmltvTimes.parse(reader.getAttributeValue(null, "stop"), defaultZone);
        Map<String, String> texts = new HashMap<>();
        String icon = null;
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String element = reader.getLocalName();
                if (depth == 1 && Set.of("title", "sub-title", "desc", "category").contains(element)) {
                    String text = readText(reader);
                    if (!text.isEmpty()) {
                        texts.putIfAbsent(element, text);
                    }
                } else {
                    if (depth == 1 && "icon".equals(element) && icon == null) {
                        icon = reader.getAttributeValue(null, "src");
                    }
                    depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return new Programme(channelId, channelName, start, stop, texts.get("title"), texts.get("sub-title"),
                texts.get("desc"), texts.get("category"), icon == null || icon.isBlank() ? null : icon.strip());
    }

    /**
     * @return the text of the element the reader is positioned at, including the text of nested elements
     */
    private static String readText(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                text.append(reader.getText());
            }
        }
        // some sources contain line breaks written as "\n" (backslash, n) instead of real ones
        return text.toString().replace("\\r\\n", "\n").replace("\\n", "\n").strip();
    }

    private static void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }
}

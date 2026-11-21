package org.bitpunk.m3uedit.plugins.enricher.epg;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.i18n.Messages;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Writes a programme guide (XMLTV) containing only the given channels, taken from the sources they have been
 * assigned to, with their programmes within a time window.
 * <p>
 * XMLTV requires all channels before all programmes, while a source may mix them, so the programmes are buffered
 * in a temporary file. The elements are copied as they are, except for the channel ids, which are written as
 * used by the playlist.
 */
@Slf4j
final class GuideCompiler {

    /**
     * The channels to take from a source.
     *
     * @param file     the local copy of the source
     * @param channels the ids of the channels in the source, mapped to the id written to the guide
     */
    record SourceSelection(Path file, Map<String, String> channels) {
    }

    /**
     * The programmes written.
     *
     * @param channelCount   the number of channels written
     * @param programmeCount the number of programmes written
     */
    record Statistics(int channelCount, int programmeCount) {
    }

    private final Instant notBefore;
    private final Instant notAfter;
    private final ZoneId defaultZone;

    /**
     * @param now        the current time
     * @param past       programmes that ended earlier than this before now are left out, null to keep all
     * @param ahead      programmes that start later than this after now are left out, null to keep all
     */
    GuideCompiler(Instant now, Duration past, Duration ahead, ZoneId defaultZone) {
        this.notBefore = past == null ? null : now.minus(past);
        this.notAfter = ahead == null ? null : now.plus(ahead);
        this.defaultZone = defaultZone;
    }

    Statistics write(List<SourceSelection> selections, OutputStream outputStream) throws IOException {
        Path programmes = Files.createTempFile("m3uedit-programmes-", ".xml");
        try {
            XMLOutputFactory factory = XMLOutputFactory.newFactory();
            int channelCount = 0;
            int programmeCount = 0;
            XMLStreamWriter guide = factory.createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
            try (OutputStream programmeStream = new BufferedOutputStream(Files.newOutputStream(programmes))) {
                XMLStreamWriter programmeWriter = factory.createXMLStreamWriter(programmeStream, StandardCharsets.UTF_8.name());
                guide.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
                guide.writeCharacters("\n");
                guide.writeDTD("<!DOCTYPE tv SYSTEM \"xmltv.dtd\">");
                guide.writeCharacters("\n");
                guide.writeStartElement("tv");
                guide.writeAttribute("generator-info-name", "m3uedit");
                guide.writeCharacters("\n");
                for (SourceSelection selection : selections) {
                    int[] counts = copySource(selection, guide, programmeWriter);
                    channelCount += counts[0];
                    programmeCount += counts[1];
                }
                programmeWriter.flush();
                programmeWriter.close();
            }
            // closes the start-tag of <tv>, so the buffered programmes can be appended to the stream directly
            guide.writeCharacters("\n");
            guide.flush();
            Files.copy(programmes, outputStream);
            guide.writeEndElement();
            guide.writeCharacters("\n");
            guide.writeEndDocument();
            guide.flush();
            guide.close();
            log.info("compiled guide with {} channels and {} programmes", channelCount, programmeCount);
            return new Statistics(channelCount, programmeCount);
        } catch (XMLStreamException e) {
            throw new IOException(Messages.get("epgEnricher.guide.writeFailed", e.getMessage()), e);
        } finally {
            Files.deleteIfExists(programmes);
        }
    }

    /**
     * @return the number of channels and programmes copied
     */
    private int[] copySource(SourceSelection selection, XMLStreamWriter guide, XMLStreamWriter programmes)
            throws IOException, XMLStreamException {
        int channels = 0;
        int programmeCount = 0;
        java.util.Set<String> written = new java.util.HashSet<>();
        try (InputStream inputStream = XmltvFiles.open(selection.file())) {
            XMLStreamReader reader = XmltvFiles.createReader(inputStream);
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    String element = reader.getLocalName();
                    if ("channel".equals(element)) {
                        String id = selection.channels().get(reader.getAttributeValue(null, "id"));
                        if (id == null || !written.add(id)) {
                            skipElement(reader);
                        } else {
                            guide.writeCharacters("  ");
                            copyElement(reader, guide, "id", id);
                            guide.writeCharacters("\n");
                            channels++;
                        }
                    } else if ("programme".equals(element)) {
                        String id = selection.channels().get(reader.getAttributeValue(null, "channel"));
                        if (id == null || !isWithinTimeWindow(reader)) {
                            skipElement(reader);
                        } else {
                            programmes.writeCharacters("  ");
                            copyElement(reader, programmes, "channel", id);
                            programmes.writeCharacters("\n");
                            programmeCount++;
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException(Messages.get("epgEnricher.guide.invalidSource", selection.file(), e.getMessage()), e);
        }
        return new int[]{channels, programmeCount};
    }

    private boolean isWithinTimeWindow(XMLStreamReader reader) {
        Instant start = XmltvTimes.parse(reader.getAttributeValue(null, "start"), defaultZone);
        Instant stop = XmltvTimes.parse(reader.getAttributeValue(null, "stop"), defaultZone);
        if (notAfter != null && start != null && start.isAfter(notAfter)) {
            return false;
        }
        // without stop time, a programme ends when the next one starts: the start time is used then
        Instant end = stop != null ? stop : start;
        return notBefore == null || end == null || !end.isBefore(notBefore);
    }

    /**
     * Copies the element the reader is positioned at (a start-element) with all its content.
     *
     * @param idAttribute the attribute whose value is replaced by {@code id}
     */
    private static void copyElement(XMLStreamReader reader, XMLStreamWriter writer, String idAttribute, String id)
            throws XMLStreamException {
        int depth = 0;
        do {
            switch (reader.getEventType()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    writer.writeStartElement(reader.getLocalName());
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        String name = reader.getAttributeLocalName(i);
                        boolean replaced = depth == 0 && idAttribute.equals(name);
                        writer.writeAttribute(name, replaced ? id : reader.getAttributeValue(i));
                    }
                    depth++;
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    writer.writeEndElement();
                    depth--;
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE -> {
                    if (!reader.isWhiteSpace()) {
                        writer.writeCharacters(reader.getText());
                    }
                }
                case XMLStreamConstants.CDATA -> writer.writeCharacters(reader.getText());
                default -> {
                    // comments and processing instructions are left out
                }
            }
            if (depth == 0) {
                return;
            }
            reader.next();
        } while (true);
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

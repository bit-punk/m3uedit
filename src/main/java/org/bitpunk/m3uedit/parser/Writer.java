package org.bitpunk.m3uedit.parser;

import org.bitpunk.m3uedit.model.Entry;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes entries as extended m3u playlist (UTF-8 encoded).
 */
public class Writer {

    private static final String M3U_START_MARKER = "#EXTM3U";
    private static final String M3U_INFO_MARKER = "#EXTINF:";
    private static final String M3U_VLC_OPT_MARKER = "#EXTVLCOPT:";
    /**
     * Duration used if an entry has none, as {@code #EXTINF} requires one (-1: unknown / live stream).
     */
    static final String DEFAULT_DURATION = "-1";

    public void write(List<Entry> entries, OutputStream outputStream) {
        write(entries, List.of(), outputStream);
    }

    /**
     * @param epgUrls the urls of the EPG (XMLTV) files written to the header, none if empty
     */
    public void write(List<Entry> entries, List<String> epgUrls, OutputStream outputStream) {
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        printWriter.print(header(epgUrls));
        for (Entry entry : entries) {
            StringBuilder builder = new StringBuilder();
            builder.append(System.lineSeparator());
            appendExtInfo(entry, builder);
            appendExtVlcOptions(entry, builder);
            builder.append(singleLine(entry.getChannelUri()));
            printWriter.print(builder);
        }
        printWriter.flush();
    }

    /**
     * The EPG urls are written as {@code x-tvg-url} and {@code url-tvg}, as players support either of them.
     */
    static String header(List<String> epgUrls) {
        StringBuilder builder = new StringBuilder(M3U_START_MARKER);
        String urls = epgUrls == null ? "" : epgUrls.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        if (!urls.isEmpty()) {
            appendAttribute(builder, "x-tvg-url", urls);
            appendAttribute(builder, "url-tvg", urls);
        }
        return builder.toString();
    }

    private void appendExtInfo(Entry entry, StringBuilder builder) {
        builder.append(M3U_INFO_MARKER);
        String duration = entry.getDuration();
        builder.append(duration == null || duration.isBlank() ? DEFAULT_DURATION : singleLine(duration.strip()));
        appendAttribute(builder, "tvg-id", entry.getTvgId());
        appendAttribute(builder, "tvg-name", entry.getTvgName());
        appendAttribute(builder, "tvg-shift", entry.getTvgShift());
        appendAttribute(builder, "radio", entry.getRadio());
        appendAttribute(builder, "tvg-logo", entry.getTvgLogo());
        appendAttribute(builder, "group-title", entry.getGroupTitle());
        if (entry.getName() != null) {
            builder.append(',').append(singleLine(entry.getName()));
        }
        builder.append(System.lineSeparator());
    }

    private void appendExtVlcOptions(Entry entry, StringBuilder builder) {
        Map<String, String> vlcOpts = entry.getVlcOpts();
        if (vlcOpts == null) {
            return;
        }
        for (Map.Entry<String, String> vlcOpt : vlcOpts.entrySet()) {
            builder.append(M3U_VLC_OPT_MARKER)
                    .append(singleLine(vlcOpt.getKey()))
                    .append('=')
                    .append(singleLine(vlcOpt.getValue()))
                    .append(System.lineSeparator());
        }
    }

    private static void appendAttribute(StringBuilder builder, String attributeName, String value) {
        if (value != null) {
            builder.append(' ').append(attributeName).append("=\"").append(attributeValue(value)).append('"');
        }
    }

    /**
     * m3u has no escaping for attribute values: a double quote would end the value, so it is replaced by a single quote.
     */
    static String attributeValue(String value) {
        return singleLine(value).replace('"', '\'');
    }

    /**
     * Every m3u directive has to fit into a single line, so line breaks are replaced by blanks.
     */
    static String singleLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
    }
}

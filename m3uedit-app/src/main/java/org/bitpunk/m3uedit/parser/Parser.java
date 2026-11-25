package org.bitpunk.m3uedit.parser;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.builder.Builder;
import org.bitpunk.m3uedit.model.EntryList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Parser {
    private static final String M3U_START_MARKER = "#EXTM3U";
    private static final String M3U_INFO_MARKER = "#EXTINF:";
    private static final String M3U_VLC_OPT_MARKER_REGEX = "^#EXTVLCOPT:? ?";
    private static final Pattern DURATION_REGEX = Pattern.compile(".*#EXTINF:([-+]?(\\d*[.])?\\d+).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TVG_ID_REGEX = Pattern.compile(".*tvg-id=\"(.?|.+?)\".*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TVG_NAME_REGEX = Pattern.compile(".*tvg-name=\"(.?|.+?)\".*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TVG_LOGO_REGEX = Pattern.compile(".*tvg-logo=\"(.?|.+?)\".*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TVG_SHIFT_REGEX = Pattern.compile(".*tvg-shift=\"(.?|.+?)\".*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_TITLE_REGEX = Pattern.compile(".*group-title=\"(.?|.+?)\".*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RADIO_REGEX = Pattern.compile(".*radio=\"(.?|.+?)\".*", Pattern.CASE_INSENSITIVE);
    /**
     * The header attributes referencing EPG files; players support either of them, so both are read.
     */
    private static final Pattern EPG_URL_REGEX = Pattern.compile("(?:^|\\s)(?:x-tvg-url|url-tvg)=\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VLC_OPTS = Pattern.compile(M3U_VLC_OPT_MARKER_REGEX + "(.*)=(.*)", Pattern.CASE_INSENSITIVE);

    private static void checkForValidBuilder(Builder builder, int lineNumber) {
        if (builder == null) {
            throw new ParsingException(lineNumber, "Missing " + M3U_INFO_MARKER);
        }
    }

    /**
     * Parses a m3u file reading its content from the given {@link InputStream}.
     *
     * @param stream pointing to your m3u file
     * @return a List of parsed Entry objects
     */
    public EntryList parse(InputStream stream) {
        if (stream == null) {
            throw new ParsingException(0, "Cannot read stream");
        }
        EntryList entries = new EntryList();
        int lineNumber = 0;
        String line;
        try {
            BufferedReader buffer = new BufferedReader(new InputStreamReader(stream));
            line = buffer.readLine();
            if (line == null) {
                throw new ParsingException(0, "Empty stream");
            }
            lineNumber++;
            checkStart(line);
            String globalTvgShift = extract(line, TVG_SHIFT_REGEX);
            entries.setEpgUrls(extractEpgUrls(line));

            Builder builder = null;
            while ((line = buffer.readLine()) != null) {
                lineNumber++;
                line = line.strip();
                if (line.isEmpty()) {
                    continue;   // blank lines carry no information
                }
                if (isExtInfo(line)) {
                    builder = extractExtInfo(globalTvgShift, line);
                } else if (isExtVlcOpt(line)) {
                    checkForValidBuilder(builder, lineNumber);
                    Map<String, String> vlcOptions = extractExtVlcOpt(line);
                    builder.vlcOptions(vlcOptions);
                } else if (isComment(line)) {
                    log.debug("ignoring unsupported directive in line {}: {}", lineNumber, line);
                } else {
                    checkForValidBuilder(builder, lineNumber);
                    entries.add(builder.channelUri(line).build());
                    builder = null; // each #EXTINF describes exactly one channel-uri
                }
            }
        } catch (IOException e) {
            throw new ParsingException(lineNumber, "Cannot read file", e);
        }

        return entries;
    }

    private void checkStart(String line) {
        if (line != null && (!line.contains(M3U_START_MARKER))) {
            throw new ParsingException(1, "First line of the file should be " + M3U_START_MARKER);
        }
    }

    /**
     * Returns the EPG urls of the {@code #EXTM3U} header line, in their order and without duplicates.
     * Each attribute may contain several comma-separated urls.
     */
    static List<String> extractEpgUrls(String headerLine) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = EPG_URL_REGEX.matcher(headerLine);
        while (matcher.find()) {
            for (String url : matcher.group(1).split(",")) {
                if (!url.isBlank()) {
                    urls.add(url.strip());
                }
            }
        }
        return new ArrayList<>(urls);
    }

    private boolean isExtInfo(String line) {
        return line.startsWith(M3U_INFO_MARKER);
    }

    /**
     * Lines starting with '#' that are neither #EXTINF nor #EXTVLCOPT (e.g. #EXTGRP, plain comments).
     */
    private boolean isComment(String line) {
        return line.startsWith("#");
    }

    private boolean isExtVlcOpt(String line) {
        Matcher matcher = VLC_OPTS.matcher(line);
        return matcher.matches();
    }

    private Builder extractExtInfo(String globalTvgShift, String line) {
        String duration = extract(line, DURATION_REGEX);
        String tvgId = extract(line, TVG_ID_REGEX);
        String tvgName = extract(line, TVG_NAME_REGEX);
        String tvgShift = extract(line, TVG_SHIFT_REGEX);
        if (tvgShift == null) {
            tvgShift = globalTvgShift;
        }
        String radio = extract(line, RADIO_REGEX);
        String tvgLogo = extract(line, TVG_LOGO_REGEX);
        String groupTitle = extract(line, GROUP_TITLE_REGEX);
        String channelName = extractChannelName(line);
        // @formatter:off
        return new Builder().channelName(channelName)
                .duration(duration)
                .groupTitle(groupTitle)
                .radio(radio)
                .tvgId(tvgId)
                .tvgLogo(tvgLogo)
                .tvgName(tvgName)
                .tvgShift(tvgShift);
        // @formatter:on
    }

    private Map<String, String> extractExtVlcOpt(String line) {
        Map<String, String> vlcOptions = new HashMap<>();
        Matcher matcher = VLC_OPTS.matcher(line);
        if (matcher.matches()) {
            vlcOptions.put(matcher.group(1), matcher.group(2));
        }
        return vlcOptions;
    }

    /**
     * Returns the channel name, which is the text after the first comma that is not part of a quoted attribute value.
     * Commas inside the channel name itself are preserved.
     *
     * @return the stripped channel name, or null if the line contains no (non-blank) channel name
     */
    static String extractChannelName(String extInfLine) {
        boolean inQuotes = false;
        for (int i = 0; i < extInfLine.length(); i++) {
            char c = extInfLine.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                String name = extInfLine.substring(i + 1).strip();
                return name.isEmpty() ? null : name;
            }
        }
        return null;
    }

    private String extract(String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}
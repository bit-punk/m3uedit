package org.bitpunk.m3uedit.parser;

import org.bitpunk.m3uedit.builder.Builder;
import org.bitpunk.m3uedit.model.EntryList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CHANNEL_NAME_REGEX = Pattern.compile(".*,(.+)$", Pattern.CASE_INSENSITIVE);
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

            Builder builder = null;
            while ((line = buffer.readLine()) != null) {
                lineNumber++;
                if (isExtInfo(line)) {
                    builder = extractExtInfo(globalTvgShift, line);
                } else if (isExtVlcOpt(line)) {
                    checkForValidBuilder(builder, lineNumber);
                    Map<String, String> vlcOptions = extractExtVlcOpt(line);
                    builder.addVlcOptions(vlcOptions);
                } else {
                    checkForValidBuilder(builder, lineNumber);
                    entries.add(builder.channelUri(line).build());
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

    private boolean isExtInfo(String line) {
        return line.contains(M3U_INFO_MARKER);
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
        String channelName = extract(line, CHANNEL_NAME_REGEX);
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

    private String extract(String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}
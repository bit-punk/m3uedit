package org.bitpunk.m3uedit.util;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.parser.ParsingException;
import org.bitpunk.m3uedit.parser.Writer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Entries of a playlist in the clipboard: as M3U text, so they can be pasted into another project, into a text editor,
 * and M3U text copied from elsewhere can be pasted into a playlist.
 */
public final class PlaylistClipboard {
    private static final String HEADER = "#EXTM3U";

    private PlaylistClipboard() {
        // Utility class
    }

    /**
     * @return the entries as M3U text, with all their attributes and VLC options
     */
    public static String toM3u(List<Entry> entries) {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        new Writer().write(entries, text);
        return text.toString(StandardCharsets.UTF_8);
    }

    /**
     * @param text M3U text, also without the {@code #EXTM3U} header (e.g. some lines copied from a web page)
     * @return the entries of the text, empty if it contains none
     */
    public static List<Entry> fromM3u(String text) {
        if (text == null || !text.contains("#EXTINF")) {
            return List.of();
        }
        String playlist = text.strip().startsWith(HEADER) ? text.strip() : HEADER + "\n" + text.strip();
        try {
            return List.copyOf(new Parser().parse(new ByteArrayInputStream(playlist.getBytes(StandardCharsets.UTF_8))));
        } catch (ParsingException e) {
            return List.of();
        }
    }
}

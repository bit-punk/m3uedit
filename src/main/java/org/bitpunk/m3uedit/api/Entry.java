package org.bitpunk.m3uedit.api;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A channel of a playlist: its name, its stream URL, the attributes of its {@code #EXTINF} line and its VLC options
 * ({@code #EXTVLCOPT}).
 * <p>
 * Entries are compared by identity: a playlist may contain several entries with the same values (e.g. the same
 * channel from two inputs), and removing or moving one of them must not affect the others. A plugin changing an entry
 * (e.g. an enricher) returns a changed copy instead of changing the entry it got.
 */
@Getter
@Setter
@ToString
public class Entry implements NamedItem {
    /**
     * The stream URLs accepted for an entry.
     */
    public static final String REGEX_URL = "^(https?|ftp|file|rtsp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
    /**
     * The values accepted for the duration.
     */
    public static final String REGEX_NUMERIC = "[+-]?\\d*";

    /**
     * The position shown in the playlist (starting with 1), set by m3uedit; it is not written to the playlist.
     */
    private int index;
    /**
     * The duration of the {@code #EXTINF} line, {@code -1} for streams.
     */
    private String duration;
    private String groupTitle;
    private String tvgId;
    private String tvgName;
    private String tvgLogo;
    private String tvgShift;
    private String radio;
    /**
     * The URL of the stream.
     */
    private String channelUri;
    /**
     * The name shown by the player.
     */
    private String name;
    /**
     * The VLC options ({@code #EXTVLCOPT:key=value}).
     */
    private Map<String, String> vlcOpts;

    /**
     * @return a new entry with the same values (and a copy of the VLC options), e.g. to be changed by an enricher
     */
    public Entry copy() {
        Entry copy = new Entry();
        copy.index = index;
        copy.duration = duration;
        copy.groupTitle = groupTitle;
        copy.tvgId = tvgId;
        copy.tvgName = tvgName;
        copy.tvgLogo = tvgLogo;
        copy.tvgShift = tvgShift;
        copy.radio = radio;
        copy.channelUri = channelUri;
        copy.name = name;
        copy.vlcOpts = vlcOpts == null ? null : new LinkedHashMap<>(vlcOpts);
        return copy;
    }
}

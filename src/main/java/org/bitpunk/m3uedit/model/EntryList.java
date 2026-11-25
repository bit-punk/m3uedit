package org.bitpunk.m3uedit.model;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Playlist;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * The entries of a playlist, plus the data from its {@code #EXTM3U} header line.
 */
public class EntryList extends ArrayList<Entry> {
    /**
     * The urls of the EPG (XMLTV) files referenced by the playlist header ({@code x-tvg-url} / {@code url-tvg}).
     */
    @Getter
    private List<String> epgUrls = List.of();

    public void setEpgUrls(List<String> epgUrls) {
        this.epgUrls = epgUrls == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(epgUrls.stream().filter(Objects::nonNull).toList()));
    }

    /**
     * @return the entries as playlist, with the EPG urls of the header if they are an {@link EntryList}
     */
    public static Playlist toPlaylist(List<Entry> entries) {
        return new Playlist(entries, entries instanceof EntryList entryList ? entryList.getEpgUrls() : List.of());
    }
}

package org.bitpunk.m3uedit.api;

import java.util.List;

/**
 * A playlist read by an input: its entries and the URLs of programme guides referenced by its header
 * ({@code x-tvg-url} / {@code url-tvg}).
 *
 * @param entries the entries in their order
 * @param epgUrls the URLs of programme guides, empty if there are none
 */
public record Playlist(List<Entry> entries, List<String> epgUrls) {

    public Playlist {
        entries = entries == null ? List.of() : List.copyOf(entries);
        epgUrls = epgUrls == null ? List.of() : List.copyOf(epgUrls);
    }

    public static Playlist of(List<Entry> entries) {
        return new Playlist(entries, List.of());
    }

    public static Playlist empty() {
        return new Playlist(List.of(), List.of());
    }
}

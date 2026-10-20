package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * Represents a single playlist published by iptv-org, e.g. all channels of a country ("countries/de.m3u").
 */
@Data
@RequiredArgsConstructor
public class IptvOrgPlaylist {
    private final PlaylistGrouping grouping;
    private final String id;
    private final String name;

    public String getPath() {
        return "%s/%s.m3u".formatted(grouping.getPath(), id.toLowerCase(Locale.ROOT));
    }

    public String toString() {
        return "%s: %s (%s)".formatted(grouping.getLabel(), name, id);
    }
}

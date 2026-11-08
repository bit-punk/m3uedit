package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Locale;

/**
 * Represents a single playlist published by iptv-org, e.g. all channels of a country ("countries/de.m3u"), or a single
 * channel (e.g. "3sat.de") whose streams are read from the playlist containing all streams.
 */
@Data
public class IptvOrgPlaylist {
    public static final String ALL_STREAMS_PATH = "index.m3u";
    private final PlaylistGrouping grouping;
    private final String id;
    private final String name;

    @JsonCreator
    public IptvOrgPlaylist(@JsonProperty("grouping") PlaylistGrouping grouping, @JsonProperty("id") String id,
                           @JsonProperty("name") String name) {
        this.grouping = grouping;
        this.id = id;
        this.name = name;
    }

    public String getPath() {
        if (grouping.isChannel()) {
            return ALL_STREAMS_PATH;
        }
        return "%s/%s.m3u".formatted(grouping.getPath(), id.toLowerCase(Locale.ROOT));
    }

    public String getSourceName() {
        return grouping.isChannel() ? "%s/%s".formatted(grouping.getPath(), id) : getPath();
    }

    public String toString() {
        return "%s: %s (%s)".formatted(grouping.getLabel(), name, id);
    }
}

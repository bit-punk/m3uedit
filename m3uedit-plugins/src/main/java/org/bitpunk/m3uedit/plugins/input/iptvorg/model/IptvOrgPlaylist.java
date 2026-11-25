package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bitpunk.m3uedit.api.PluginContext;

import java.util.Locale;

/**
 * Represents a single playlist published by iptv-org, e.g. all channels of a country ("countries/de.m3u"), or a single
 * channel (e.g. "3sat.de") whose streams are read from the playlist containing all streams.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IptvOrgPlaylist {
    public static final String ALL_STREAMS_PATH = "index.m3u";
    private PlaylistGrouping grouping;
    private String id;
    private String name;

    public String getPath() {
        if (grouping.isChannel()) {
            return ALL_STREAMS_PATH;
        }
        return "%s/%s.m3u".formatted(grouping.getPath(), id.toLowerCase(Locale.ROOT));
    }

    public String getSourceName() {
        return grouping.isChannel() ? "%s/%s".formatted(grouping.getPath(), id) : getPath();
    }

    /**
     * @return the playlist as shown to the user, e.g. "Country: Germany (DE)"
     */
    public String describe(PluginContext context) {
        return "%s: %s (%s)".formatted(grouping.getLabel(context), name, id);
    }

    /**
     * @return e.g. "COUNTRY: Germany (DE)", see {@link #describe(PluginContext)} for the text shown to the user
     */
    @Override
    public String toString() {
        return "%s: %s (%s)".formatted(grouping, name, id);
    }
}

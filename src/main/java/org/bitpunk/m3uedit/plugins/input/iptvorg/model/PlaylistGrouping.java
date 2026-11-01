package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import lombok.Getter;

/**
 * The groupings in which iptv-org publishes its playlists. Each grouping maps to a sub-path of the playlist base-url
 * (e.g. "countries/de.m3u") and to a json-file of the iptv-org API listing the available codes (e.g. "countries.json").
 * The grouping {@link #CHANNEL} is special, as iptv-org provides no playlist per channel: its channels are taken from
 * and read out of the playlist containing all streams ("index.m3u").
 */
@Getter
public enum PlaylistGrouping {
    COUNTRY("Country", "countries", "code"),
    CATEGORY("Category", "categories", "id"),
    LANGUAGE("Language", "languages", "code"),
    REGION("Region", "regions", "code"),
    CHANNEL("Channel", "channels", null);

    private final String label;
    private final String path;
    private final String idField;

    PlaylistGrouping(String label, String path, String idField) {
        this.label = label;
        this.path = path;
        this.idField = idField;
    }

    public boolean isChannel() {
        return this == CHANNEL;
    }

    @Override
    public String toString() {
        return label;
    }
}

package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import lombok.Getter;

/**
 * The groupings in which iptv-org publishes its playlists. Each grouping maps to a sub-path of the playlist base-url
 * (e.g. "countries/de.m3u") and to a json-file of the iptv-org API listing the available codes (e.g. "countries.json").
 */
@Getter
public enum PlaylistGrouping {
    COUNTRY("Country", "countries", "code"),
    CATEGORY("Category", "categories", "id"),
    LANGUAGE("Language", "languages", "code"),
    REGION("Region", "regions", "code");

    private final String label;
    private final String path;
    private final String idField;

    PlaylistGrouping(String label, String path, String idField) {
        this.label = label;
        this.path = path;
        this.idField = idField;
    }

    @Override
    public String toString() {
        return label;
    }
}

package org.bitpunk.m3uedit.plugins.input.iptvorg.model;

import lombok.Getter;
import org.bitpunk.m3uedit.i18n.Messages;

/**
 * The groupings in which iptv-org publishes its playlists. Each grouping maps to a sub-path of the playlist base-url
 * (e.g. "countries/de.m3u") and to a json-file of the iptv-org API listing the available codes (e.g. "countries.json").
 * The grouping {@link #CHANNEL} is special, as iptv-org provides no playlist per channel: its channels are taken from
 * and read out of the playlist containing all streams ("index.m3u").
 */
@Getter
public enum PlaylistGrouping {
    COUNTRY("iptvorg.grouping.country", "countries", "code"),
    CATEGORY("iptvorg.grouping.category", "categories", "id"),
    LANGUAGE("iptvorg.grouping.language", "languages", "code"),
    REGION("iptvorg.grouping.region", "regions", "code"),
    CHANNEL("iptvorg.grouping.channel", "channels", null);

    /**
     * The key of the label shown to the user.
     */
    private final String labelKey;
    private final String path;
    private final String idField;

    PlaylistGrouping(String labelKey, String path, String idField) {
        this.labelKey = labelKey;
        this.path = path;
        this.idField = idField;
    }

    /**
     * @return the label shown to the user, e.g. "Country"
     */
    public String getLabel() {
        return Messages.get(labelKey);
    }

    public boolean isChannel() {
        return this == CHANNEL;
    }

    @Override
    public String toString() {
        return getLabel();
    }
}

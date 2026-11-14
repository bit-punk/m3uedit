package org.bitpunk.m3uedit.plugins.enricher.epg;

/**
 * The ways an entry of the playlist is assigned to a channel of the programme guide, tried in this order.
 */
public enum MatchStrategy {
    /**
     * The tvg-id of the entry is the id of a channel (ignoring the case).
     */
    TVG_ID("tvg-id"),
    /**
     * A rule of the user assigns a channel id to the stream url of the entry.
     */
    URL_RULE("URL rule"),
    /**
     * The tvg-name or name of the entry is a display name of a channel (normalized, e.g. without "HD").
     */
    NAME("name"),
    /**
     * The first words of the tvg-name or name of the entry are a display name of a channel, e.g. "WDR Köln" and
     * "WDR". This is a guess, so it is tried last.
     */
    NAME_PREFIX("name prefix");

    private final String label;

    MatchStrategy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

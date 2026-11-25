package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.api.PluginContext;

/**
 * The ways an entry of the playlist is assigned to a channel of the programme guide, tried in this order.
 */
public enum MatchStrategy {
    /**
     * The tvg-id of the entry is the id of a channel (ignoring the case).
     */
    TVG_ID("epgEnricher.strategy.tvgId"),
    /**
     * A rule of the user assigns a channel id to the stream url of the entry.
     */
    URL_RULE("epgEnricher.strategy.urlRule"),
    /**
     * The tvg-name or name of the entry is a display name of a channel (normalized, e.g. without "HD").
     */
    NAME("epgEnricher.strategy.name"),
    /**
     * The first words of the tvg-name or name of the entry are a display name of a channel, e.g. "WDR Köln" and
     * "WDR". This is a guess, so it is tried last.
     */
    NAME_PREFIX("epgEnricher.strategy.namePrefix");

    private final String labelKey;

    MatchStrategy(String labelKey) {
        this.labelKey = labelKey;
    }

    /**
     * @param context the context of the enricher, providing its texts
     * @return the name shown to the user, e.g. in the diff ("by name")
     */
    public String getLabel(PluginContext context) {
        return context.text(labelKey);
    }
}

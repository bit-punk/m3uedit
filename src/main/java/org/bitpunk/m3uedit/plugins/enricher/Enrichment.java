package org.bitpunk.m3uedit.plugins.enricher;

import org.bitpunk.m3uedit.model.Entry;

import java.util.List;

/**
 * The result of an {@link Enricher}.
 *
 * @param entries     the enriched entries, in the order they have been passed to the enricher
 * @param attachments the files to write next to the playlist, none if empty
 */
public record Enrichment(List<Entry> entries, List<Attachment> attachments) {

    public Enrichment {
        entries = List.copyOf(entries);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * @return a result with the given entries and without additional files
     */
    public static Enrichment of(List<Entry> entries) {
        return new Enrichment(entries, List.of());
    }
}

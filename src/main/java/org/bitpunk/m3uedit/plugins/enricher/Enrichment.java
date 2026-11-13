package org.bitpunk.m3uedit.plugins.enricher;

import org.bitpunk.m3uedit.model.Entry;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of an {@link Enricher}.
 *
 * @param entries     the enriched entries, in the order they have been passed to the enricher
 * @param attachments the files to write next to the playlist, none if empty
 * @param notes       a short note for entries worth mentioning in the diff (e.g. "no EPG data"), by enriched entry
 * @param summary     a short summary of the result shown to the user (e.g. "62 of 70 entries have EPG data"),
 *                    null if none
 * @param warnings    problems the user should know about, e.g. a source that could not be read
 * @param epgUrls     EPG urls the playlist may reference if no other EPG urls are set, none if empty
 */
public record Enrichment(List<Entry> entries, List<Attachment> attachments, Map<Entry, String> notes,
                         String summary, List<String> warnings, List<String> epgUrls) {

    public Enrichment {
        entries = List.copyOf(entries);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        notes = notes == null ? Map.of() : Collections.unmodifiableMap(new IdentityHashMap<>(notes));
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        epgUrls = epgUrls == null ? List.of() : List.copyOf(epgUrls);
    }

    public Enrichment(List<Entry> entries, List<Attachment> attachments) {
        this(entries, attachments, null, null, null, null);
    }

    /**
     * @return a result with the given entries and without additional files
     */
    public static Enrichment of(List<Entry> entries) {
        return new Enrichment(entries, List.of());
    }
}

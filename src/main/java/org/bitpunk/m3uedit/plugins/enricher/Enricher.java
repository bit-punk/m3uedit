package org.bitpunk.m3uedit.plugins.enricher;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.util.List;

/**
 * An enricher completes the entries of the playlist, e.g. by setting their {@code tvg-id}, and may provide
 * additional files written by the outputs next to the playlist, e.g. a programme guide.
 * <p>
 * Enrichers run after the sorters, in their order. They neither remove, add nor reorder entries, and they must not
 * change the name or the channel-uri of an entry: these identify an entry for the manual changes of the user.
 */
public interface Enricher extends Plugin {

    /**
     * Enriches the given entries. Runs in the background, so it may block (e.g. read from the network).
     * <p>
     * The entries passed in must not be modified, as they are shared with the previous steps of the pipeline:
     * a changed entry has to be returned as a new object (e.g. via {@link org.bitpunk.m3uedit.util.EntryMapper}),
     * an unchanged entry as the same object.
     *
     * @param entries the sorted entries
     * @return the entries in the same order, and the additional files
     */
    Enrichment enrich(List<Entry> entries);

    /**
     * Describes why an entry has been changed by this enricher, shown to the user in the diff of the playlist.
     * Called on the JavaFX application thread for single entries only, so it must not block (e.g. no network).
     *
     * @param before the entry passed to {@link #enrich(List)}
     * @param after  the changed entry returned for it
     * @return a short reason, or null if a description of the changed attributes is sufficient
     */
    default String describeChange(Entry before, Entry after) {
        return null;
    }
}

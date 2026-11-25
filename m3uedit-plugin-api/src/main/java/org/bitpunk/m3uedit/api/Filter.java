package org.bitpunk.m3uedit.api;

import java.util.List;

/**
 * Removes entries from the playlist. The enabled filters are applied one after another, see {@link #order()}.
 */
public interface Filter extends Plugin {

    /**
     * Filters judging each entry on its own, cheap (e.g. by its name).
     */
    int ORDER_SELECTION = 100;
    /**
     * Filters judging each entry on its own, but expensive (e.g. requesting its stream): applied after the cheap
     * ones, so fewer entries are checked.
     */
    int ORDER_PROBING = 200;
    /**
     * Filters removing duplicates: they keep one entry of each group, so they are applied after all filters judging
     * single entries, otherwise they might keep an entry removed afterwards and the channel would be lost.
     */
    int ORDER_DEDUPLICATION = 300;
    /**
     * Filters removing variants of channels kept (e.g. SD channels with an HD variant): applied last, as they depend
     * on the variants left by all other filters.
     */
    int ORDER_VARIANTS = 400;

    /**
     * The position of this filter in the pipeline: filters are always applied in ascending order (see the
     * {@code ORDER_} constants), as the result of a filter depending on the other entries depends on the filters
     * applied before. Filters with the same order are applied in the order of the project.
     */
    default int order() {
        return ORDER_SELECTION;
    }

    /**
     * Filters the entries. Runs in the background, so it may block (e.g. request streams).
     *
     * @param entries the entries, not to be modified
     * @return the entries kept, in their order (the same objects)
     */
    List<Entry> filter(List<Entry> entries);

    /**
     * Describes why an entry has been removed by this filter, shown to the user in the diff of the playlist.
     * Called on the JavaFX application thread for single entries only, so it must not block (e.g. no network).
     *
     * @param removed the entry removed by {@link #filter(List)}
     * @param input   the list passed to {@link #filter(List)}, containing the removed entry
     * @return a short reason, or null if there is no reason more specific than the name of the filter
     */
    default String describeRemoval(Entry removed, List<Entry> input) {
        return null;
    }
}

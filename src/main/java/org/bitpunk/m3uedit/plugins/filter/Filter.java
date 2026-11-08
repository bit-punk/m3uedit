package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.util.List;


/**
 * The Filter interface represents a plugin that can be used to filter a list of entries.
 * It extends the Plugin interface.
 */
public interface Filter extends Plugin {

    /**
     * Filters a list of entries based on certain criteria.
     * Returns a new list containing only the filtered entries.
     *
     * @param list The list of entries to filter
     * @return The filtered list of entries
     */
    List<Entry> filter(List<Entry> list);

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

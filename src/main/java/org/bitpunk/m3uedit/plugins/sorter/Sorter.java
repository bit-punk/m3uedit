package org.bitpunk.m3uedit.plugins.sorter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.util.List;

/**
 * Sorter is an interface that represents a plugin used to sort a list of entries.
 * It extends the Plugin interface.
 */
public interface Sorter extends Plugin {
    /**
     * Sorts a list of entries based on their names in alphabetical order.
     *
     * @param entries The list of entries to be sorted
     * @return A new list containing the sorted entries
     */
    List<Entry> sort(List<Entry> entries);
}
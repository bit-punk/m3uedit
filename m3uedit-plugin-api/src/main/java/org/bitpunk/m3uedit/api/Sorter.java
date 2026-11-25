package org.bitpunk.m3uedit.api;

import java.util.List;

/**
 * Sorts the entries of the playlist completely; at most one sorter of a project is enabled.
 */
public interface Sorter extends Plugin {

    /**
     * Sorts the entries. Runs in the background.
     *
     * @param entries the entries, not to be modified
     * @return the same entries (the same objects) in a new order
     */
    List<Entry> sort(List<Entry> entries);
}

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
}

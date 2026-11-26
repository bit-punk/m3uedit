package org.bitpunk.m3uedit.plugins.missing;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Sorter;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * A sorter of a project-file that is not installed: it is skipped, as if it was disabled.
 */
public class MissingSorter extends MissingPlugin implements Sorter {

    public MissingSorter(String typeId, ObjectNode settings) {
        super(typeId, settings);
    }

    @Override
    public List<Entry> sort(List<Entry> entries) {
        return entries;
    }
}

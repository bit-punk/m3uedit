package org.bitpunk.m3uedit.plugins.missing;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * A filter of a project-file that is not installed: it is skipped, as if it was disabled.
 */
public class MissingFilter extends MissingPlugin implements Filter {

    public MissingFilter(String typeId, ObjectNode settings) {
        super(typeId, settings);
    }

    @Override
    public List<Entry> filter(List<Entry> entries) {
        return entries;
    }
}

package org.bitpunk.m3uedit.plugins.sorter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.util.List;

public interface Sorter extends Plugin {
    List<Entry> sort(List<Entry> entries);
}
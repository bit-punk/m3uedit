package org.bitpunk.m3uedit.plugins.sorter;

import org.bitpunk.m3uedit.model.Entry;

import java.util.List;

public interface Sorter {

    List<Entry> sort(List<Entry> entries);
    String getName();

    String getDescription();
}
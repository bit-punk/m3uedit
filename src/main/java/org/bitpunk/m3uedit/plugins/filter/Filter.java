package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.model.Entry;

import java.util.List;

public interface Filter {
    List<Entry> filter(List<Entry> list);

    String getName();
    String getDescription();

}

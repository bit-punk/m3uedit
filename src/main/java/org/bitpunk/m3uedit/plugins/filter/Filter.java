package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.util.List;

public interface Filter extends Plugin {
    List<Entry> filter(List<Entry> list);
}

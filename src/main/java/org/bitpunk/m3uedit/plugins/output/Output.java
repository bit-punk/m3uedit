package org.bitpunk.m3uedit.plugins.output;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.util.List;

public interface Output extends Plugin {
    void write(List<Entry> playlist);

    void configure();

    String getOutput();
}
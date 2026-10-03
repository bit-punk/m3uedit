package org.bitpunk.m3uedit.plugins.input;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.io.FileNotFoundException;
import java.util.List;

public interface Input extends Plugin {
    List<Entry> read() throws FileNotFoundException;
    String getSource();

    @Override
    default boolean isConfigurable() {
        return true;
    }
}
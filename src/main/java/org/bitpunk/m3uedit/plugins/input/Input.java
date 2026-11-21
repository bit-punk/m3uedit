package org.bitpunk.m3uedit.plugins.input;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.io.FileNotFoundException;
import java.util.List;

public interface Input extends Plugin {
    /**
     * Reads the entries of this input.
     *
     * @return the entries read, empty if the input is not configured yet
     * @throws InputReadException if the input, or a part of it, could not be read; it contains the entries of the
     *                            parts that could be read
     * @throws FileNotFoundException if the file of the input does not exist
     */
    List<Entry> read() throws FileNotFoundException;
    String getSource();

    @Override
    default boolean isConfigurable() {
        return true;
    }
}
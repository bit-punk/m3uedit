package org.bitpunk.m3uedit.plugins.output;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.net.URI;
import java.util.List;

public interface Output extends Plugin {
    /**
     * Writes the playlist and the additional files of the enrichers next to it.
     * Runs in the background; dialogs (e.g. asking for a password) have to be shown on the JavaFX application thread.
     */
    void write(Export export);

    default void write(List<Entry> playlist) {
        write(Export.of(playlist));
    }

    void configure();

    URI getOutput();

    @Override
    default boolean isConfigurable() {
        return true;
    }
}
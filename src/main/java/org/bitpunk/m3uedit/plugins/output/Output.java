package org.bitpunk.m3uedit.plugins.output;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.net.URI;
import java.util.List;

public interface Output extends Plugin {
    /**
     * @param epgUrls the urls of the EPG (XMLTV) files to reference in the playlist header, none if empty
     */
    void write(List<Entry> playlist, List<String> epgUrls);

    default void write(List<Entry> playlist) {
        write(playlist, List.of());
    }

    void configure();

    URI getOutput();

    @Override
    default boolean isConfigurable() {
        return true;
    }
}
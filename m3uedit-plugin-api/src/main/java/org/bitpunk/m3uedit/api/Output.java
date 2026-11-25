package org.bitpunk.m3uedit.api;

import java.net.URI;

/**
 * Writes the playlist and the additional files of the enrichers, e.g. to a file or a server.
 */
public interface Output extends Plugin {

    /**
     * Writes the playlist and the additional files of the enrichers next to it. Runs in the background; dialogs (e.g.
     * asking for a password) have to be shown on the JavaFX application thread.
     *
     * @throws PluginException if it could not be written; its message is shown to the user
     */
    void write(Export export);

    /**
     * @return where the playlist is written to, shown to the user (never containing a password); null while the
     * output is not configured
     */
    URI getOutput();

    @Override
    default boolean isConfigurable() {
        return true;
    }
}

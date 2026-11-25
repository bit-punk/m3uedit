package org.bitpunk.m3uedit.api;

import java.io.IOException;

/**
 * Reads the entries of a playlist, e.g. from a file, a URL or a device. The entries of all inputs of a project are
 * merged in the order of the inputs.
 */
public interface Input extends Plugin {

    /**
     * Reads the entries of this input. Runs in the background, so it may block (e.g. read from the network); it must
     * not open dialogs except on the JavaFX application thread.
     *
     * @return the playlist read, empty if the input is not configured yet
     * @throws InputReadException if the input, or a part of it, could not be read; it contains the entries of the
     *                            parts that could be read
     * @throws IOException        if the input could not be read; its message is shown to the user
     */
    Playlist read() throws IOException;

    /**
     * @return the source of the entries shown to the user (e.g. a file name or URL, never a password); null while the
     * input is not configured
     */
    String getSource();

    @Override
    default boolean isConfigurable() {
        return true;
    }
}

package org.bitpunk.m3uedit.plugins.input;

import org.bitpunk.m3uedit.model.Entry;

import java.util.List;

/**
 * Thrown by {@link Input#read()} if the input, or a part of it, could not be read, e.g. one stream of a FritzBox or one
 * playlist of iptv-org. The entries of the parts that could be read are kept and used nevertheless, the error is
 * reported like the error of a whole input.
 */
public class InputReadException extends RuntimeException {
    private final transient List<Entry> entriesRead;

    /**
     * @param message     what could not be read and why, e.g. "tvhd.m3u: status-code 404"
     * @param entriesRead the entries of the parts that could be read, empty if nothing could be read
     */
    public InputReadException(String message, List<Entry> entriesRead) {
        super(message);
        this.entriesRead = entriesRead == null ? List.of() : entriesRead;
    }

    public InputReadException(String message, Throwable cause) {
        super(message, cause);
        this.entriesRead = List.of();
    }

    /**
     * @return the entries of the parts that could be read
     */
    public List<Entry> getEntriesRead() {
        return entriesRead;
    }
}

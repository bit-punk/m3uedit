package org.bitpunk.m3uedit.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Identifies an entry by its name and channel-uri, e.g. to re-apply a manual change after the entries have been
 * re-computed from the inputs (which creates new entry-objects).
 * <p>
 * Several entries may have the same key (e.g. the same channel from two inputs): a key always refers to the first
 * of them.
 */
public record EntryKey(@JsonProperty("name") String name, @JsonProperty("channelUri") String channelUri) {

    public static EntryKey of(Entry entry) {
        return new EntryKey(entry.getName(), entry.getChannelUri());
    }

    public boolean matches(Entry entry) {
        return Objects.equals(name, entry.getName()) && Objects.equals(channelUri, entry.getChannelUri());
    }

    /**
     * @return the index of the first entry with this key, or -1 if there is none
     */
    public int indexIn(List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (matches(entries.get(i))) {
                return i;
            }
        }
        return -1;
    }
}

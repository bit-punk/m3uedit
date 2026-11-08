package org.bitpunk.m3uedit.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bitpunk.m3uedit.util.EntryMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A change of the playlist made manually by the user: removing, adding, editing or moving an entry.
 * <p>
 * The playlist is re-computed from the inputs, filters and sorters, so manual changes are recorded and re-applied
 * to each newly computed playlist, in the order they have been made. Entries are referred to by their
 * {@link EntryKey}; a change referring to an entry which does not exist (anymore) is skipped.
 *
 * @param action the kind of change
 * @param target the entry removed, edited or moved (not used for {@link Action#ADD})
 * @param entry  the values of the entry added or edited (only used for {@link Action#ADD} and {@link Action#EDIT})
 * @param after  the entry a moved entry is placed after, null to place it first (only used for {@link Action#MOVE})
 */
public record ManualChange(@JsonProperty("action") Action action,
                           @JsonProperty("target") EntryKey target,
                           @JsonProperty("entry") Entry entry,
                           @JsonProperty("after") EntryKey after) {

    public enum Action {
        /** removes the target */
        REMOVE,
        /** adds the entry at the end of the playlist */
        ADD,
        /** replaces the target by the entry */
        EDIT,
        /** moves the target behind the entry 'after', or to the start of the playlist */
        MOVE
    }

    public ManualChange {
        Objects.requireNonNull(action, "action must not be null");
    }

    public static ManualChange remove(Entry removed) {
        return new ManualChange(Action.REMOVE, EntryKey.of(removed), null, null);
    }

    public static ManualChange add(Entry added) {
        return new ManualChange(Action.ADD, null, copyOf(added), null);
    }

    public static ManualChange edit(EntryKey target, Entry edited) {
        return new ManualChange(Action.EDIT, target, copyOf(edited), null);
    }

    /**
     * @param after the entry the moved entry is placed after, null if it is placed first
     */
    public static ManualChange move(Entry moved, Entry after) {
        return new ManualChange(Action.MOVE, EntryKey.of(moved), null, after == null ? null : EntryKey.of(after));
    }

    /**
     * Applies the changes in the given order to a copy of the entries.
     * Entries added or edited are new objects, the given entries are not modified.
     *
     * @return the entries with the changes applied
     */
    public static List<Entry> applyAll(List<Entry> entries, List<ManualChange> changes) {
        List<Entry> result = new ArrayList<>(entries);
        for (ManualChange change : changes) {
            change.applyTo(result);
        }
        return result;
    }

    /**
     * Applies this change to the given (modifiable) list of entries.
     */
    void applyTo(List<Entry> entries) {
        switch (action) {
            case ADD -> {
                if (entry != null) {
                    entries.add(copyOf(entry));
                }
            }
            case REMOVE -> {
                int index = indexOfTarget(entries);
                if (index >= 0) {
                    entries.remove(index);
                }
            }
            case EDIT -> {
                int index = indexOfTarget(entries);
                if (index >= 0 && entry != null) {
                    entries.set(index, copyOf(entry));
                }
            }
            case MOVE -> move(entries);
        }
    }

    private void move(List<Entry> entries) {
        int index = indexOfTarget(entries);
        if (index < 0) {
            return;
        }
        Entry moved = entries.remove(index);
        if (after == null) {
            entries.addFirst(moved);
            return;
        }
        int afterIndex = after.indexIn(entries);
        // the entry it was placed after does not exist anymore: the entry stays where it is
        entries.add(afterIndex >= 0 ? afterIndex + 1 : index, moved);
    }

    private int indexOfTarget(List<Entry> entries) {
        return target == null ? -1 : target.indexIn(entries);
    }

    private static Entry copyOf(Entry entry) {
        Entry copy = new Entry();
        EntryMapper.mapEntry(entry, copy);
        return copy;
    }
}

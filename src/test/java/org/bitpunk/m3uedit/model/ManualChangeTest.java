package org.bitpunk.m3uedit.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManualChangeTest {

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name);
        return entry;
    }

    private static List<String> names(List<Entry> entries) {
        return entries.stream().map(Entry::getName).toList();
    }

    private static List<Entry> entries(String... names) {
        return Arrays.stream(names).map(ManualChangeTest::entry).toList();
    }

    @Test
    void testRemove() {
        List<Entry> result = ManualChange.applyAll(entries("a", "b", "c"), List.of(ManualChange.remove(entry("b"))));

        assertThat(names(result)).containsExactly("a", "c");
    }

    @Test
    void testRemovingADuplicateKeepsTheOtherOne() {
        List<Entry> result = ManualChange.applyAll(entries("a", "same", "b", "same"),
                List.of(ManualChange.remove(entry("same"))));

        assertThat(names(result)).containsExactly("a", "b", "same");
    }

    @Test
    void testAddAppendsACopy() {
        Entry added = entry("new");

        List<Entry> result = ManualChange.applyAll(entries("a"), List.of(ManualChange.add(added)));

        assertThat(names(result)).containsExactly("a", "new");
        assertThat(result.get(1)).isNotSameAs(added);
        assertThat(result.get(1).getChannelUri()).isEqualTo("http://example.com/new");
    }

    @Test
    void testEditReplacesTheTargetByACopy() {
        List<Entry> entries = entries("a", "b");
        Entry edited = entry("b");
        edited.setGroupTitle("Favorites");

        List<Entry> result = ManualChange.applyAll(entries, List.of(ManualChange.edit(EntryKey.of(entries.get(1)), edited)));

        assertThat(names(result)).containsExactly("a", "b");
        assertThat(result.get(1).getGroupTitle()).isEqualTo("Favorites");
        // the computed entries are not modified
        assertThat(entries.get(1).getGroupTitle()).isNull();
    }

    @Test
    void testMoveBehindAnotherEntry() {
        List<Entry> result = ManualChange.applyAll(entries("a", "b", "c", "d"),
                List.of(ManualChange.move(entry("a"), entry("c"))));

        assertThat(names(result)).containsExactly("b", "c", "a", "d");
    }

    @Test
    void testMoveToTheStart() {
        List<Entry> result = ManualChange.applyAll(entries("a", "b", "c"), List.of(ManualChange.move(entry("c"), null)));

        assertThat(names(result)).containsExactly("c", "a", "b");
    }

    @Test
    void testMovedEntryStaysNextToItsPredecessorWhenNewEntriesAreComputed() {
        List<ManualChange> changes = List.of(ManualChange.move(entry("a"), entry("c")));

        // later, the inputs provide new entries
        List<Entry> result = ManualChange.applyAll(entries("new1", "a", "b", "c", "new2", "d"), changes);

        assertThat(names(result)).containsExactly("new1", "b", "c", "a", "new2", "d");
    }

    @Test
    void testChangesOfMissingEntriesAreSkipped() {
        List<ManualChange> changes = List.of(
                ManualChange.remove(entry("gone")),
                ManualChange.edit(EntryKey.of(entry("gone")), entry("edited")),
                ManualChange.move(entry("gone"), null),
                // the entry it was placed after is gone: the entry stays where it is
                ManualChange.move(entry("b"), entry("gone")));

        List<Entry> result = ManualChange.applyAll(entries("a", "b", "c"), changes);

        assertThat(names(result)).containsExactly("a", "b", "c");
    }

    @Test
    void testChangesAreAppliedInTheirOrder() {
        Entry added = entry("new");
        Entry edited = entry("renamed");
        List<ManualChange> changes = List.of(
                ManualChange.add(added),
                ManualChange.edit(EntryKey.of(added), edited),
                ManualChange.move(edited, null),
                ManualChange.remove(entry("a")));

        List<Entry> result = ManualChange.applyAll(entries("a", "b"), changes);

        assertThat(names(result)).containsExactly("renamed", "b");
    }

    @Test
    void testTheGivenEntriesAreNotModified() {
        List<Entry> entries = new ArrayList<>(entries("a", "b"));

        ManualChange.applyAll(entries, List.of(ManualChange.remove(entry("a")), ManualChange.add(entry("c"))));

        assertThat(names(entries)).containsExactly("a", "b");
    }
}

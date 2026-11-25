package org.bitpunk.m3uedit.model;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.model.EntryDiff.Kind;
import org.bitpunk.m3uedit.model.EntryDiff.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EntryDiffTest {

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        return entry;
    }

    private static List<Entry> entries(String... names) {
        return java.util.Arrays.stream(names).map(EntryDiffTest::entry).toList();
    }

    @Test
    void identicalListsAreUnchanged() {
        List<Entry> list = entries("a", "b", "c");

        EntryDiff diff = EntryDiff.compute(list, list);

        assertThat(diff.getRows()).hasSize(3).allMatch(Row::isUnchanged);
        assertThat(diff.getUnchangedCount()).isEqualTo(3);
        assertThat(diff.getRemovedCount()).isZero();
        assertThat(diff.getMovedCount()).isZero();
        assertThat(diff.getRows().get(1).leftPosition()).isEqualTo(2);
        assertThat(diff.getRows().get(1).rightPosition()).isEqualTo(2);
    }

    @Test
    void removedEntriesHaveAnEmptyRightSide() {
        List<Entry> left = entries("a", "b", "c", "d");
        List<Entry> right = List.of(left.get(0), left.get(3));

        EntryDiff diff = EntryDiff.compute(left, right);

        assertThat(diff.getRows()).extracting(Row::leftKind)
                .containsExactly(Kind.UNCHANGED, Kind.REMOVED, Kind.REMOVED, Kind.UNCHANGED);
        assertThat(diff.getRows().get(1).right()).isNull();
        assertThat(diff.getRows().get(3).rightPosition()).isEqualTo(2);
        assertThat(diff.getRemovedCount()).isEqualTo(2);
    }

    @Test
    void movedEntryIsShownOnBothSides() {
        List<Entry> left = entries("a", "b", "c", "d");
        // d moved to the top
        List<Entry> right = List.of(left.get(3), left.get(0), left.get(1), left.get(2));

        EntryDiff diff = EntryDiff.compute(left, right);

        assertThat(diff.getMovedCount()).isEqualTo(1);
        assertThat(diff.getRemovedCount()).isZero();
        Row first = diff.getRows().getFirst();
        assertThat(first.left()).isNull();
        assertThat(first.right()).isSameAs(left.get(3));
        assertThat(first.rightKind()).isEqualTo(Kind.MOVED);
        Row last = diff.getRows().getLast();
        assertThat(last.left()).isSameAs(left.get(3));
        assertThat(last.leftKind()).isEqualTo(Kind.MOVED);
        assertThat(last.right()).isNull();
    }

    @Test
    void changedEntriesBetweenUnchangedOnesShareRows() {
        List<Entry> left = entries("a", "b", "c");
        Entry added = entry("x");
        // b removed, x added at its place
        List<Entry> right = List.of(left.get(0), added, left.get(2));

        EntryDiff diff = EntryDiff.compute(left, right);

        assertThat(diff.getRows()).hasSize(3);
        Row changed = diff.getRows().get(1);
        assertThat(changed.left()).isSameAs(left.get(1));
        assertThat(changed.leftKind()).isEqualTo(Kind.REMOVED);
        assertThat(changed.right()).isSameAs(added);
        assertThat(changed.rightKind()).isEqualTo(Kind.ADDED);
        assertThat(diff.getAddedCount()).isEqualTo(1);
    }

    @Test
    void equalEntriesAreComparedByIdentity() {
        List<Entry> left = entries("a", "a");
        List<Entry> right = List.of(left.get(1));

        EntryDiff diff = EntryDiff.compute(left, right);

        assertThat(diff.getRows()).hasSize(2);
        assertThat(diff.getRows().get(0).leftKind()).isEqualTo(Kind.REMOVED);
        assertThat(diff.getRows().get(0).left()).isSameAs(left.get(0));
        assertThat(diff.getRows().get(1).isUnchanged()).isTrue();
    }

    @Test
    void emptyLists() {
        assertThat(EntryDiff.compute(List.of(), List.of()).getRows()).isEmpty();
        assertThat(EntryDiff.compute(entries("a"), List.of()).getRemovedCount()).isEqualTo(1);
        assertThat(EntryDiff.compute(List.of(), entries("a")).getAddedCount()).isEqualTo(1);
    }

    @Test
    void movedCountIsMinimalForAShuffledList() {
        List<Entry> left = IntStream.range(0, 2000).mapToObj(i -> entry("e" + i)).toList();
        List<Entry> right = new ArrayList<>(left);
        Collections.shuffle(right, new Random(42));

        EntryDiff diff = EntryDiff.compute(left, right);

        // every entry shows up exactly once on each side
        assertThat(diff.getRows().stream().filter(row -> row.left() != null).count()).isEqualTo(2000);
        assertThat(diff.getRows().stream().filter(row -> row.right() != null).count()).isEqualTo(2000);
        assertThat(diff.getMovedCount() + diff.getUnchangedCount()).isEqualTo(2000);
        // the unchanged entries keep their order on both sides
        List<Row> unchanged = diff.getRows().stream().filter(Row::isUnchanged).toList();
        for (int k = 1; k < unchanged.size(); k++) {
            assertThat(unchanged.get(k).leftPosition()).isGreaterThan(unchanged.get(k - 1).leftPosition());
            assertThat(unchanged.get(k).rightPosition()).isGreaterThan(unchanged.get(k - 1).rightPosition());
        }
    }

    @Test
    void reversedListKeepsOneEntryUnchanged() {
        List<Entry> left = entries("a", "b", "c", "d");
        List<Entry> right = left.reversed();

        EntryDiff diff = EntryDiff.compute(left, right);

        assertThat(diff.getUnchangedCount()).isEqualTo(1);
        assertThat(diff.getMovedCount()).isEqualTo(3);
    }

    @Test
    void testCopiesAreMatchedWithTheirOriginAndShownAsModified() {
        Entry a = entry("a");
        Entry b = entry("b");
        Entry c = entry("c");
        Entry changedB = entry("b");
        changedB.setTvgId("B.de");
        java.util.Map<Entry, Entry> origins = new java.util.IdentityHashMap<>();
        origins.put(changedB, b);

        EntryDiff diff = EntryDiff.compute(List.of(a, b, c), List.of(a, changedB, c), entry -> origins.getOrDefault(entry, entry));

        assertThat(diff.getModifiedCount()).isEqualTo(1);
        assertThat(diff.getUnchangedCount()).isEqualTo(2);
        assertThat(diff.getRemovedCount()).isZero();
        assertThat(diff.getAddedCount()).isZero();
        Row modified = diff.getRows().get(1);
        assertThat(modified.leftKind()).isEqualTo(Kind.MODIFIED);
        assertThat(modified.rightKind()).isEqualTo(Kind.MODIFIED);
        assertThat(modified.left()).isSameAs(b);
        assertThat(modified.right()).isSameAs(changedB);
        assertThat(modified.isUnchanged()).isFalse();
        assertThat(modified.isAnchor()).isTrue();
    }

    @Test
    void testCopiesWithoutOriginAreRemovedAndAdded() {
        Entry b = entry("b");
        Entry changedB = entry("b");

        EntryDiff diff = EntryDiff.compute(List.of(b), List.of(changedB));

        assertThat(diff.getRemovedCount()).isEqualTo(1);
        assertThat(diff.getAddedCount()).isEqualTo(1);
        assertThat(diff.getModifiedCount()).isZero();
    }
}

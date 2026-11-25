package org.bitpunk.m3uedit.model;

import org.bitpunk.m3uedit.api.Entry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryTest {

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name);
        return entry;
    }

    @Test
    void testEntriesWithTheSameValuesAreNotEqual() {
        Entry entry = entry("same");
        Entry duplicate = entry("same");

        assertThat(entry).isEqualTo(entry).isNotEqualTo(duplicate);
    }

    @Test
    void testHashCodeDoesNotChangeWhenEditingAnEntry() {
        Entry entry = entry("before");
        int hashCode = entry.hashCode();

        entry.setName("after");
        entry.setChannelUri("http://example.com/after");

        assertThat(entry.hashCode()).isEqualTo(hashCode);
    }

    @Test
    void testRemovingADuplicateKeepsTheOtherOne() {
        // as done when deleting the selected entries of a table
        Entry first = entry("same");
        Entry second = entry("same");
        Entry other = entry("other");
        ObservableList<Entry> entries = FXCollections.observableArrayList(first, other, second);

        entries.removeAll(List.of(second));

        assertThat(entries).containsExactly(first, other);
        assertThat(entries.getFirst()).isSameAs(first);
    }

    @Test
    void testToStringShowsTheValues() {
        assertThat(entry("ZDF")).asString().contains("name=ZDF").contains("channelUri=http://example.com/ZDF");
    }
}

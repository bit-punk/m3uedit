package org.bitpunk.m3uedit.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.enricher.Enrichment;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputReadException;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.util.EntryMapper;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Simple plugins for controller tests.
 */
final class TestPlugins {

    private TestPlugins() {
    }

    static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name.replace(' ', '_'));
        return entry;
    }

    static class TestInput implements Input {
        private final String name;
        private final List<Entry> entries;
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);
        final AtomicInteger configured = new AtomicInteger();
        /**
         * The error reported when reading, the entries are read nevertheless (a part of the input); null if none.
         */
        String error;

        TestInput(String name, List<Entry> entries) {
            this.name = name;
            this.entries = entries;
        }

        @Override
        public List<Entry> read() {
            if (error != null) {
                throw new InputReadException(error, entries);
            }
            return entries;
        }

        @Override
        public String getSource() {
            return name + "-source";
        }

        @Override
        public void configure() {
            configured.incrementAndGet();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public BooleanProperty getEnabled() {
            return enabled;
        }
    }

    /**
     * Removes the entries matching a predicate.
     */
    static class TestFilter implements Filter {
        private final String name;
        private final Predicate<Entry> removed;
        private final BooleanProperty enabled = new SimpleBooleanProperty(false);
        final AtomicInteger configured = new AtomicInteger();

        TestFilter(String name, Predicate<Entry> removed) {
            this.name = name;
            this.removed = removed;
        }

        @Override
        public List<Entry> filter(List<Entry> list) {
            return list.stream().filter(removed.negate()).toList();
        }

        @Override
        public String describeRemoval(Entry entry, List<Entry> input) {
            return "test reason for " + entry.getName();
        }

        @Override
        public boolean isConfigurable() {
            return true;
        }

        @Override
        public void configure() {
            configured.incrementAndGet();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name + " description";
        }

        @Override
        public BooleanProperty getEnabled() {
            return enabled;
        }
    }

    /**
     * Sets the tvg-id of each entry to its name in upper case.
     */
    static class TestEnricher implements Enricher {
        private final BooleanProperty enabled = new SimpleBooleanProperty(false);

        @Override
        public Enrichment enrich(List<Entry> entries) {
            return Enrichment.of(entries.stream().map(entry -> {
                Entry enriched = EntryMapper.copyOf(entry);
                enriched.setTvgId(entry.getName().toUpperCase());
                return enriched;
            }).toList());
        }

        @Override
        public String describeChange(Entry before, Entry after) {
            return "test match";
        }

        @Override
        public String getName() {
            return "Test Enricher";
        }

        @Override
        public String getDescription() {
            return "sets the tvg-id";
        }

        @Override
        public BooleanProperty getEnabled() {
            return enabled;
        }
    }

    static class NameSorter implements Sorter {
        private final BooleanProperty enabled = new SimpleBooleanProperty(false);

        @Override
        public List<Entry> sort(List<Entry> entries) {
            return entries.stream().sorted(Comparator.comparing(Entry::getName)).toList();
        }

        @Override
        public String getName() {
            return "Name Sorter";
        }

        @Override
        public String getDescription() {
            return "sorts by name";
        }

        @Override
        public BooleanProperty getEnabled() {
            return enabled;
        }
    }
}

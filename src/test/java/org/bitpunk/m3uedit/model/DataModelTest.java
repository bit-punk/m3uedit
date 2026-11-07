package org.bitpunk.m3uedit.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.file.FileOutput;
import org.bitpunk.m3uedit.plugins.sorter.channelname.ChannelNameSorter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DataModelTest {

    /**
     * Collects tasks to run them later, to simulate long-running background work.
     */
    private static class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            tasks.removeFirst().run();
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }

        int size() {
            return tasks.size();
        }
    }

    private static class TestInput implements Input {
        private final String name;
        private final List<Entry> entries;
        private final RuntimeException failure;
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);
        final AtomicInteger readCount = new AtomicInteger();

        TestInput(String name, List<Entry> entries) {
            this(name, entries, null);
        }

        TestInput(String name, List<Entry> entries, RuntimeException failure) {
            this.name = name;
            this.entries = entries;
            this.failure = failure;
        }

        @Override
        public List<Entry> read() throws FileNotFoundException {
            readCount.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return entries;
        }

        @Override
        public String getSource() {
            return name + "-source";
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

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name);
        return entry;
    }

    private static List<String> names(List<Entry> entries) {
        return entries.stream().map(Entry::getName).toList();
    }

    private QueuedExecutor background;
    private QueuedExecutor fx;
    private List<String> errors;
    private DataModel dataModel;
    private UniqueNameFilter uniqueNameFilter;
    private ChannelNameSorter channelNameSorter;

    @BeforeEach
    void setUp() {
        background = new QueuedExecutor();
        fx = new QueuedExecutor();
        errors = new ArrayList<>();
        dataModel = new DataModel("test");
        dataModel.setBackgroundExecutor(background);
        dataModel.setFxExecutor(fx);
        dataModel.setErrorHandler(errors::add);
        uniqueNameFilter = new UniqueNameFilter();
        channelNameSorter = new ChannelNameSorter();
        dataModel.getFilters().add(uniqueNameFilter);
        dataModel.getSorters().add(channelNameSorter);
    }

    private void runAll() {
        while (background.size() > 0 || fx.size() > 0) {
            background.runAll();
            fx.runAll();
        }
    }

    @Test
    void testAddingInputLoadsFiltersAndSortsInBackground() {
        uniqueNameFilter.getEnabled().set(true);
        channelNameSorter.getEnabled().set(true);
        TestInput input = new TestInput("in", List.of(entry("b"), entry("a"), entry("b")));

        dataModel.getInputs().add(input);

        // nothing is read on the calling thread
        assertThat(input.readCount).hasValue(0);
        assertThat(dataModel.isBusy()).isTrue();
        assertThat(dataModel.getRawEntryList()).isEmpty();

        background.runAll();
        // results are applied on the fx-thread only
        assertThat(dataModel.getRawEntryList()).isEmpty();
        fx.runAll();

        assertThat(input.readCount).hasValue(1);
        assertThat(names(dataModel.getRawEntryList())).containsExactly("b", "a", "b");
        assertThat(names(dataModel.getFilteredEntryList())).containsExactly("b", "a");
        assertThat(names(dataModel.getSortedEntryList())).containsExactly("a", "b");
        assertThat(dataModel.isBusy()).isFalse();
        assertThat(errors).isEmpty();
    }

    @Test
    void testDisabledPluginsAreNotApplied() {
        dataModel.getInputs().add(new TestInput("in", List.of(entry("b"), entry("a"), entry("b"))));
        runAll();

        assertThat(names(dataModel.getFilteredEntryList())).containsExactly("b", "a", "b");
        assertThat(names(dataModel.getSortedEntryList())).containsExactly("b", "a", "b");
    }

    @Test
    void testFilterAndSortDoNotReloadInputs() {
        TestInput input = new TestInput("in", List.of(entry("b"), entry("a"), entry("b")));
        dataModel.getInputs().add(input);
        runAll();

        uniqueNameFilter.getEnabled().set(true);
        dataModel.filterRawEntries();
        runAll();
        assertThat(names(dataModel.getSortedEntryList())).containsExactly("b", "a");

        channelNameSorter.getEnabled().set(true);
        dataModel.sortFilteredEntries();
        runAll();
        assertThat(names(dataModel.getSortedEntryList())).containsExactly("a", "b");

        assertThat(input.readCount).hasValue(1);
    }

    @Test
    void testEachListIsUpdatedWithASingleChange() {
        uniqueNameFilter.getEnabled().set(true);
        AtomicInteger rawChanges = new AtomicInteger();
        AtomicInteger filteredChanges = new AtomicInteger();
        AtomicInteger sortedChanges = new AtomicInteger();
        dataModel.getRawEntryList().addListener((javafx.collections.ListChangeListener<Entry>) c -> rawChanges.incrementAndGet());
        dataModel.getFilteredEntryList().addListener((javafx.collections.ListChangeListener<Entry>) c -> filteredChanges.incrementAndGet());
        dataModel.getSortedEntryList().addListener((javafx.collections.ListChangeListener<Entry>) c -> sortedChanges.incrementAndGet());

        dataModel.getInputs().add(new TestInput("in", List.of(entry("b"), entry("a"), entry("b"))));
        runAll();

        assertThat(rawChanges).hasValue(1);
        assertThat(filteredChanges).hasValue(1);
        assertThat(sortedChanges).hasValue(1);
        // applying results does not trigger another computation
        assertThat(background.size()).isZero();
    }

    @Test
    void testOutdatedResultsAreDiscarded() {
        dataModel.getInputs().add(new TestInput("first", List.of(entry("first"))));
        dataModel.getInputs().add(new TestInput("second", List.of(entry("second"))));
        assertThat(background.size()).isEqualTo(2);

        // the second (newer) computation finishes first
        background.tasks.removeLast().run();
        fx.runAll();
        assertThat(names(dataModel.getRawEntryList())).containsExactly("first", "second");
        assertThat(dataModel.isBusy()).isFalse();

        // the older computation only saw the first input and must not overwrite the newer result
        background.runAll();
        fx.runAll();
        assertThat(names(dataModel.getRawEntryList())).containsExactly("first", "second");
    }

    @Test
    void testFilteringWhileLoadingStillAppliesTheLoadedEntries() {
        dataModel.getInputs().add(new TestInput("in", List.of(entry("a"))));
        // filter requested while the inputs are still being loaded
        dataModel.filterRawEntries();

        runAll();

        assertThat(names(dataModel.getRawEntryList())).containsExactly("a");
        assertThat(names(dataModel.getSortedEntryList())).containsExactly("a");
    }

    @Test
    void testFailingInputIsSkippedAndReported() {
        TestInput failing = new TestInput("broken", List.of(), new IllegalStateException("cannot connect"));
        dataModel.getInputs().addAll(failing, new TestInput("ok", List.of(entry("a"))));
        runAll();

        assertThat(names(dataModel.getRawEntryList())).containsExactly("a");
        assertThat(errors).singleElement().asString().contains("broken (broken-source): cannot connect");
        assertThat(dataModel.isBusy()).isFalse();
    }

    @Test
    void testFailingFilterKeepsPreviousResultAndReportsError() {
        dataModel.getInputs().add(new TestInput("in", List.of(entry("a"))));
        runAll();
        // a channel without a name lets the UniqueNameFilter fail
        dataModel.getRawEntryList().add(entry(null));
        uniqueNameFilter.getEnabled().set(true);
        dataModel.getFilters().set(0, new UniqueNameFilter() {
            @Override
            public List<Entry> filter(List<Entry> list) {
                throw new IllegalStateException("filter failed");
            }

            @Override
            public BooleanProperty getEnabled() {
                return new SimpleBooleanProperty(true);
            }
        });
        dataModel.filterRawEntries();
        runAll();

        assertThat(names(dataModel.getSortedEntryList())).containsExactly("a");
        assertThat(errors).last().asString().contains("filter failed");
        assertThat(dataModel.isBusy()).isFalse();
    }

    @Test
    void testEntriesRemovedByTheUserArePropagated() {
        dataModel.getInputs().add(new TestInput("in", List.of(entry("a"), entry("b"))));
        runAll();

        dataModel.getRawEntryList().removeFirst();
        runAll();
        assertThat(names(dataModel.getSortedEntryList())).containsExactly("b");

        dataModel.getFilteredEntryList().clear();
        runAll();
        assertThat(dataModel.getSortedEntryList()).isEmpty();
    }

    @Test
    void testComputedEntriesDoNotMarkTheProjectAsChanged() {
        dataModel.getInputs().add(new TestInput("in", List.of(entry("a"))));
        dataModel.setChanged(false);

        runAll();
        dataModel.getSortedEntryList().add(entry("manual"));

        assertThat(dataModel.isChanged()).isFalse();
    }

    @Test
    void testChangedPluginsMarkTheProjectAsChanged() {
        dataModel.setChanged(false);
        dataModel.getInputs().add(new TestInput("in", List.of()));
        assertThat(dataModel.isChanged()).isTrue();
    }

    @Test
    void testDataModelsWithDifferentEntriesAreEqual() {
        DataModel other = new DataModel("test");
        other.getFilters().add(uniqueNameFilter);
        other.getSorters().add(channelNameSorter);
        other.getSortedEntryList().add(entry("a"));

        assertThat(dataModel).isEqualTo(other);
    }

    @Test
    void testTogglingAPluginMarksTheProjectAsChanged() {
        TestInput input = new TestInput("in", List.of());
        dataModel.getInputs().add(input);
        runAll();

        for (var plugin : List.of(uniqueNameFilter, channelNameSorter, input)) {
            dataModel.setChanged(false);
            plugin.getEnabled().set(!plugin.getEnabled().get());
            assertThat(dataModel.isChanged()).as(plugin.getName()).isTrue();
        }
    }

    @Test
    void testRemovedPluginsDoNotMarkTheProjectAsChanged() {
        dataModel.getFilters().remove(uniqueNameFilter);
        dataModel.setChanged(false);

        uniqueNameFilter.getEnabled().set(true);

        assertThat(dataModel.isChanged()).isFalse();
    }

    @Test
    void testReplacedListsKeepTheirListeners() {
        UniqueNameFilter newFilter = new UniqueNameFilter();
        dataModel.setFilters(javafx.collections.FXCollections.observableArrayList(newFilter));
        dataModel.setChanged(false);

        // plugins of the old list are no longer observed
        uniqueNameFilter.getEnabled().set(true);
        assertThat(dataModel.isChanged()).isFalse();

        newFilter.getEnabled().set(true);
        assertThat(dataModel.isChanged()).isTrue();

        dataModel.setChanged(false);
        dataModel.getFilters().add(new UniqueNameFilter());
        assertThat(dataModel.isChanged()).isTrue();
    }

    @Test
    void testReplacedInputListStillTriggersReload() {
        dataModel.setInputs(javafx.collections.FXCollections.observableArrayList());
        assertThat(background.size()).isZero();

        dataModel.getInputs().add(new TestInput("in", List.of(entry("a"))));
        runAll();

        assertThat(names(dataModel.getSortedEntryList())).containsExactly("a");
    }

    @Test
    void testDataModelFactoryKeepsListeners() {
        DataModel created = DataModelFactory.createDataModel("factory");
        created.setChanged(false);

        created.getFilters().getFirst().getEnabled().set(true);

        assertThat(created.isChanged()).isTrue();
    }

    private static Output output(String name, List<List<Entry>> written, RuntimeException failure) {
        return new FileOutput() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public void write(List<Entry> playlist) {
                if (failure != null) {
                    throw failure;
                }
                written.add(List.copyOf(playlist));
            }
        };
    }

    @Test
    void testFailingOutputIsReportedAndRemainingOutputsAreWritten() {
        dataModel.getInputs().add(new TestInput("in", List.of(entry("a"))));
        runAll();
        List<List<Entry>> written = new ArrayList<>();
        dataModel.getOutputs().addAll(
                output("broken", written, new RuntimeException(new java.io.IOException("disk full"))),
                output("ok", written, null));

        assertThat(dataModel.saveEntries()).isFalse();

        assertThat(written).singleElement().satisfies(playlist -> assertThat(names(playlist)).containsExactly("a"));
        assertThat(errors).singleElement().asString()
                .contains("could not be written to all outputs")
                .contains("broken: disk full");
    }

    @Test
    void testSavingToAllOutputsReportsNoError() {
        List<List<Entry>> written = new ArrayList<>();
        dataModel.getOutputs().addAll(output("first", written, null), output("second", written, null));

        assertThat(dataModel.saveEntries()).isTrue();

        assertThat(written).hasSize(2);
        assertThat(errors).isEmpty();
    }
}

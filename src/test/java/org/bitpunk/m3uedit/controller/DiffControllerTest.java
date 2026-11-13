package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bitpunk.m3uedit.controller.DiffController.FoldItem;
import org.bitpunk.m3uedit.controller.DiffController.Item;
import org.bitpunk.m3uedit.controller.DiffController.RowItem;
import org.bitpunk.m3uedit.controller.TestPlugins.NameSorter;
import org.bitpunk.m3uedit.controller.TestPlugins.TestFilter;
import org.bitpunk.m3uedit.controller.TestPlugins.TestInput;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.EntryDiff.Kind;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bitpunk.m3uedit.controller.TestPlugins.entry;

class DiffControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private DataModel dataModel;
    private TestFilter filter;
    private NameSorter sorter;
    private ObjectProperty<Plugin> selection;
    private DiffController diffController;
    private TestInput input;

    @BeforeEach
    void setUp() {
        dataModel = new DataModel("test");
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        filter = new TestFilter("Shop Filter", entry -> entry.getName().startsWith("shop"));
        sorter = new NameSorter();
        dataModel.getFilters().add(filter);
        dataModel.getSorters().add(sorter);
        selection = new SimpleObjectProperty<>();
        diffController = new DiffController();
        diffController.initModel(dataModel, selection);
        // 20 channels, every 5th one is a shopping channel
        List<Entry> entries = IntStream.range(0, 20)
                .mapToObj(i -> entry((i % 5 == 4 ? "shop " : "channel ") + (char) ('a' + i)))
                .toList();
        input = new TestInput("in", entries);
        filter.getEnabled().set(true);
        dataModel.getInputs().add(input);
    }

    private List<Item> items() {
        return diffController.getDiffListView().getItems();
    }

    private List<RowItem> rows() {
        return items().stream().filter(RowItem.class::isInstance).map(RowItem.class::cast).toList();
    }

    @Test
    void withoutSelectionTheLoadedEntriesAreComparedToThePlaylist() {
        assertThat(diffController.getDiff().getRemovedCount()).isEqualTo(4);
        assertThat(diffController.getContextLabel().getText()).startsWith("All steps");
        RowItem removed = rows().stream().filter(row -> row.row().leftKind() == Kind.REMOVED).findFirst().orElseThrow();
        assertThat(removed.row().left().getName()).isEqualTo("shop e");
    }

    @Test
    void unchangedEntriesAreFolded() {
        // changes only: the unchanged runs between the removed shopping channels are folded
        assertThat(items()).anyMatch(FoldItem.class::isInstance);
        FoldItem fold = items().stream().filter(FoldItem.class::isInstance).map(FoldItem.class::cast).findFirst().orElseThrow();
        int sizeBefore = items().size();

        diffController.expand(fold);

        assertThat(items()).hasSize(sizeBefore - 1 + fold.hiddenRows().size());
    }

    @Test
    void selectingAFilterShowsItsEffect() {
        selection.set(filter);

        assertThat(diffController.getContextLabel().getText()).isEqualTo("Shop Filter removes 4 of 20 entries.");
        assertThat(diffController.getDiff().getRemovedCount()).isEqualTo(4);
        assertThat(diffController.getDiff().getMovedCount()).isZero();
    }

    @Test
    void selectingADisabledStepShowsNoDiff() {
        selection.set(sorter);

        assertThat(diffController.getContextLabel().getText()).contains("is disabled");
        assertThat(items()).isEmpty();
    }

    @Test
    void selectingASorterShowsMovedEntries() {
        // the input is not ordered by name: 'channel' and 'shop' entries are mixed
        filter.getEnabled().set(false);
        sorter.getEnabled().set(true);
        dataModel.filterRawEntries();   // re-computed by the pipeline in the application
        selection.set(sorter);

        assertThat(diffController.getDiff().getRemovedCount()).isZero();
        // the shopping channels move behind the other channels, the last one ("shop t") is already there
        assertThat(diffController.getDiff().getMovedCount()).isEqualTo(3);
        assertThat(diffController.getStatsLabel().getText()).contains("↕ 3 moved");
    }

    @Test
    void manualChangesOfThePlaylistAreShown() {
        Entry added = entry("my channel");
        dataModel.getSortedEntryList().removeFirst();
        dataModel.getSortedEntryList().add(added);

        assertThat(diffController.getDiff().getRemovedCount()).isEqualTo(5);
        assertThat(diffController.getDiff().getAddedCount()).isEqualTo(1);
    }

    @Test
    void searchShowsMatchingRowsOnly() {
        diffController.lookupAll(".text-field").forEach(node -> ((javafx.scene.control.TextField) node).setText("shop"));

        assertThat(rows()).hasSize(4).allMatch(row -> row.row().left().getName().startsWith("shop"));
        assertThat(items()).noneMatch(FoldItem.class::isInstance);
    }

    @Test
    void selectingAnInputShowsItsEntries() {
        selection.set(input);

        assertThat(diffController.getContextLabel().getText()).isEqualTo("in delivers 20 entries, 16 of them are in the playlist.");
    }

    @Test
    void anEnricherShowsTheChangedEntriesWithTheirChanges() {
        TestPlugins.TestEnricher enricher = new TestPlugins.TestEnricher();
        dataModel.getEnrichers().add(enricher);
        enricher.getEnabled().set(true);
        dataModel.enrichSortedEntries();

        selection.set(enricher);

        assertThat(diffController.getContextLabel().getText()).isEqualTo("Test Enricher changes 16 of 16 entries.");
        assertThat(diffController.getDiff().getModifiedCount()).isEqualTo(16);
        assertThat(diffController.getStatsLabel().getText()).contains("✎ 16 changed");
        RowItem first = rows().getFirst();
        assertThat(first.row().rightKind()).isEqualTo(Kind.MODIFIED);
        assertThat(first.row().right().getTvgId()).isEqualTo("CHANNEL A");
    }

    @Test
    void enrichedEntriesAreModifiedInTheWholePipeline() {
        TestPlugins.TestEnricher enricher = new TestPlugins.TestEnricher();
        dataModel.getEnrichers().add(enricher);
        enricher.getEnabled().set(true);
        dataModel.enrichSortedEntries();

        assertThat(diffController.getDiff().getRemovedCount()).isEqualTo(4);
        assertThat(diffController.getDiff().getModifiedCount()).isEqualTo(16);
        assertThat(diffController.getDiff().getAddedCount()).isZero();

        selection.set(input);
        assertThat(diffController.getContextLabel().getText()).isEqualTo("in delivers 20 entries, 16 of them are in the playlist.");
    }

    @Test
    void entriesWithANoteOfTheEnricherAreNotFolded() {
        org.bitpunk.m3uedit.plugins.enricher.Enricher enricher = new TestPlugins.TestEnricher() {
            @Override
            public org.bitpunk.m3uedit.plugins.enricher.Enrichment enrich(List<Entry> entries) {
                java.util.Map<Entry, String> notes = new java.util.IdentityHashMap<>();
                notes.put(entries.get(7), "no EPG data");
                return new org.bitpunk.m3uedit.plugins.enricher.Enrichment(entries, List.of(), notes,
                        "15 of 16 entries have EPG data.", null, null);
            }
        };
        dataModel.getEnrichers().add(enricher);
        enricher.getEnabled().set(true);
        dataModel.enrichSortedEntries();

        selection.set(enricher);

        assertThat(diffController.getContextLabel().getText())
                .isEqualTo("Test Enricher changes 0 of 16 entries. 15 of 16 entries have EPG data.");
        assertThat(rows()).extracting(row -> row.row().right().getName())
                .containsExactly("channel g", "channel h", "channel i", "channel k", "channel l");
        assertThat(items()).anyMatch(FoldItem.class::isInstance);
    }
}

package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Playlists may contain several entries with the same values (e.g. the same channel from two inputs).
 * Moving one of them must not remove or move the others.
 */
class EntryListControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name);
        return entry;
    }

    private static EntryListController controllerWith(Entry... entries) {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        dataModel.setBackgroundExecutor(task -> {
        });
        dataModel.getSortedEntryList().setAll(entries);
        return new ProjectController(dataModel).entryListController;
    }

    private static ObservableList<Entry> items(EntryListController controller) {
        return controller.entryTableView().getItems();
    }

    @Test
    void testDraggingADuplicateKeepsTheOtherDuplicate() throws Exception {
        runOnFxThread(() -> {
            Entry a = entry("a");
            Entry duplicate1 = entry("same");
            Entry b = entry("b");
            Entry duplicate2 = entry("same");
            EntryListController controller = controllerWith(a, duplicate1, b, duplicate2);

            // drag the first duplicate to the end
            controller.moveItems(List.of(1), 4);

            assertThat(items(controller)).hasSize(4);
            assertThat(items(controller).get(0)).isSameAs(a);
            assertThat(items(controller).get(1)).isSameAs(b);
            assertThat(items(controller).get(2)).isSameAs(duplicate2);
            assertThat(items(controller).get(3)).isSameAs(duplicate1);
            // the moved entry is selected, not its duplicate
            assertThat(controller.entryTableView().getSelectionModel().getSelectedIndices()).containsExactly(3);
        });
    }

    @Test
    void testChangingTheIndexOfADuplicateMovesOnlyThatEntry() throws Exception {
        runOnFxThread(() -> {
            Entry duplicate1 = entry("same");
            Entry other = entry("other");
            Entry duplicate2 = entry("same");
            EntryListController controller = controllerWith(duplicate1, other, duplicate2);

            // the user entered index 1 for the last entry in the entry-dialog
            duplicate2.setIndex(1);
            controller.moveEntryToNewPosition(duplicate2);

            assertThat(items(controller)).hasSize(3);
            assertThat(items(controller).get(0)).isSameAs(duplicate2);
            assertThat(items(controller).get(1)).isSameAs(duplicate1);
            assertThat(items(controller).get(2)).isSameAs(other);
        });
    }

    @Test
    void testTvgIdIsShownNextToTheName() throws Exception {
        Entry zdf = entry("ZDF HD");
        zdf.setTvgId("ZDF.de");
        Entry plain = entry("Plain");
        EntryListController controller = controllerWith(zdf, plain);

        runOnFxThread(() -> {
            List<String> titles = controller.entryTableView().getColumns().stream()
                    .map(javafx.scene.control.TableColumn::getText).toList();
            assertThat(titles).containsSubsequence("Channel Name", "tvg-id", "Channel URI");
            javafx.scene.control.TableColumn<Entry, ?> tvgIdColumn = controller.entryTableView().getColumns().stream()
                    .filter(column -> "tvg-id".equals(column.getText())).findFirst().orElseThrow();
            assertThat(tvgIdColumn.getCellData(zdf)).isEqualTo("ZDF.de");
            assertThat(tvgIdColumn.getCellData(plain)).isNull();
        });
    }

    @Test
    void testEpgButtonIsEnabledWithAnEnabledEpgEnricher() throws Exception {
        EntryListController controller = controllerWith(entry("ZDF"));

        runOnFxThread(() -> {
            assertThat(controller.epgButton().isDisable()).isTrue();
            assertThat(controller.programmeGuideProviders()).isEmpty();
        });
    }
}

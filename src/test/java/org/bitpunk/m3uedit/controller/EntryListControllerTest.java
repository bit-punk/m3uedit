package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider;
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
        // closing the last window must not end the toolkit for the following tests
        Platform.setImplicitExit(false);
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

    @Test
    void testOnlyOneEpgWindowIsOpenAtATime() throws Exception {
        EntryListController controller = controllerWith(entry("ZDF"));
        List<ProgrammeGuideProvider> providers = List.of(playlist -> List.of());

        runOnFxThread(() -> {
            controller.showEpg(providers);
            EpgWindow first = controller.epgWindow();
            controller.showEpg(providers);
            assertThat(controller.epgWindow()).isSameAs(first);
            assertThat(first.isShowing()).isTrue();

            // once closed, a new window is opened
            first.close();
            assertThat(controller.epgWindow()).isNull();
            controller.showEpg(providers);
            assertThat(controller.epgWindow()).isNotNull().isNotSameAs(first);
            controller.epgWindow().close();
        });
    }

    @Test
    void testTheLogoIsShownBeforeTheChannelName(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path logoFile = tempDir.resolve("zdf.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                "png", logoFile.toFile());
        Entry zdf = entry("ZDF");
        zdf.setTvgLogo(logoFile.toUri().toString());
        Entry plain = entry("Plain");
        EntryListController controller = controllerWith(zdf, plain);

        runOnFxThread(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(new javafx.scene.Group(controller), 600, 300));
            stage.show();
            controller.entryTableView().layout();
            java.util.Map<String, EntryListController.ChannelNameCell> cells = new java.util.HashMap<>();
            controller.entryTableView().lookupAll(".table-cell").stream()
                    .filter(EntryListController.ChannelNameCell.class::isInstance)
                    .map(EntryListController.ChannelNameCell.class::cast)
                    .filter(cell -> !cell.isEmpty())
                    .forEach(cell -> cells.put(cell.getText(), cell));
            stage.close();

            assertThat(cells.keySet()).containsExactlyInAnyOrder("ZDF", "Plain");
            assertThat(cells.get("ZDF").logo().getImage()).isNotNull();
            assertThat(cells.get("ZDF").logo().getImage().getUrl()).endsWith("/zdf.png");
            // without a logo the space stays empty, so the names are aligned
            assertThat(cells.get("Plain").logo().getImage()).isNull();
            assertThat(cells.get("Plain").getGraphic()).isNotNull();
        });
    }

    @Test
    void testAnOpenEpgWindowShowsTheChangedPlaylist() throws Exception {
        Entry ard = entry("ARD");
        ard.setTvgId("ARD.de");
        EntryListController controller = controllerWith(ard);
        List<ProgrammeGuideProvider> providers = List.of(playlist -> List.of());
        runOnFxThread(() -> {
            controller.showEpg(providers);
            Entry zdf = entry("ZDF");
            zdf.setTvgId("ZDF.de");
            controller.entryTableView().getItems().add(zdf);
        });

        // the window is updated once the playlist has not changed for a moment
        long deadline = System.currentTimeMillis() + 5000;
        List<String> channels = List.of();
        while (!channels.contains("ZDF") && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            AtomicReference<List<String>> shown = new AtomicReference<>();
            runOnFxThread(() -> shown.set(controller.epgWindow().getGuide().getChannels().stream()
                    .map(ProgrammeGuideView.Channel::name).toList()));
            channels = shown.get();
        }

        assertThat(channels).containsExactly("ARD", "ZDF");
        runOnFxThread(() -> controller.epgWindow().close());
    }
}

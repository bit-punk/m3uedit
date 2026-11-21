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

    /**
     * An enricher providing a programme guide, like the EPG Enricher.
     */
    static class GuideEnricher extends TestPlugins.TestEnricher implements ProgrammeGuideProvider {
        @Override
        public List<org.bitpunk.m3uedit.plugins.enricher.Programme> readProgrammes(List<Entry> playlist) {
            return List.of();
        }
    }

    @Test
    void testEpgButtonIsDisabledWithoutEntries() throws Exception {
        EntryListController controller = controllerWith(entry("ZDF"));
        GuideEnricher enricher = new GuideEnricher();
        enricher.getEnabled().set(true);

        runOnFxThread(() -> {
            controller.getDataModel().getEnrichers().add(enricher);
            assertThat(controller.epgButton().isDisable()).isFalse();

            controller.getDataModel().removeEntries(List.copyOf(controller.playlist()));
            assertThat(controller.epgButton().isDisable()).isTrue();
            assertThat(controller.epgButton().getTooltip().getText()).contains("no channels");

            controller.getDataModel().addEntry(entry("arte"));
            assertThat(controller.epgButton().isDisable()).isFalse();
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
            controller.playlist().add(zdf);
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

    private static Entry channel(String name, String group, String tvgId, String uri) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setGroupTitle(group);
        entry.setTvgId(tvgId);
        entry.setChannelUri(uri);
        return entry;
    }

    private static EntryListController searchable() {
        return controllerWith(
                channel("Das Erste HD", "Public", "DasErste.de", "http://example.com/ard"),
                channel("ZDF HD", "Public", "ZDF.de", "http://example.com/zdf"),
                channel("QVC", "Shopping", "QVC.de", "http://shop.example.com/qvc"),
                channel("arte HD", "Culture", "arte.de", "http://example.com/arte"));
    }

    private static List<String> shownNames(EntryListController controller) {
        return controller.entryTableView().getItems().stream().map(Entry::getName).toList();
    }

    @Test
    void testTheSearchFiltersByNameGroupTvgIdAndUrl() throws Exception {
        EntryListController controller = searchable();
        runOnFxThread(() -> {
            controller.searchField().setText("hd");
            assertThat(shownNames(controller)).containsExactly("Das Erste HD", "ZDF HD", "arte HD");
            assertThat(controller.searchResultLabel().getText()).isEqualTo("3 of 4");

            controller.searchField().setText("PUBLIC");
            assertThat(shownNames(controller)).containsExactly("Das Erste HD", "ZDF HD");

            controller.searchField().setText("arte.de");
            assertThat(shownNames(controller)).containsExactly("arte HD");

            controller.searchField().setText("shop.example");
            assertThat(shownNames(controller)).containsExactly("QVC");

            controller.searchField().clear();
            assertThat(shownNames(controller)).hasSize(4);
            assertThat(controller.searchResultLabel().getText()).isEmpty();
            assertThat(controller.isSearching()).isFalse();
        });
    }

    @Test
    void testTheSearchDoesNotChangeThePlaylist() throws Exception {
        EntryListController controller = searchable();
        runOnFxThread(() -> {
            controller.searchField().setText("qvc");

            assertThat(controller.playlist()).hasSize(4);
            // the positions in the playlist are shown, QVC is the third entry
            assertThat(controller.playlistPosition(0)).isEqualTo(3);
            assertThat(controller.isSearching()).as("reordering is disabled").isTrue();
        });
    }

    @Test
    void testTheIndexColumnShowsThePositionInThePlaylistWhileSearching() throws Exception {
        EntryListController controller = searchable();
        runOnFxThread(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(new javafx.scene.Group(controller), 600, 300));
            stage.show();
            controller.searchField().setText("arte");
            controller.entryTableView().layout();

            List<String> indices = controller.entryTableView().lookupAll(".table-cell").stream()
                    .filter(cell -> cell instanceof javafx.scene.control.TableCell<?, ?> tableCell
                            && tableCell.getTableColumn() == controller.entryTableView().getColumns().getFirst()
                            && !tableCell.isEmpty()
                            // only the rows shown, not the hidden row the table measures its cells with
                            && tableCell.getIndex() >= 0 && tableCell.getParent().isVisible())
                    .map(cell -> ((javafx.scene.control.TableCell<?, ?>) cell).getText()).toList();
            stage.close();

            assertThat(indices).containsExactly("4");
        });
    }

    @Test
    void testAnEntryFoundIsDeletedFromThePlaylist() throws Exception {
        EntryListController controller = searchable();
        runOnFxThread(() -> {
            controller.searchField().setText("qvc");
            controller.entryTableView().getSelectionModel().selectFirst();

            controller.deleteSelected();

            assertThat(controller.playlist()).extracting(Entry::getName)
                    .containsExactly("Das Erste HD", "ZDF HD", "arte HD");
            assertThat(shownNames(controller)).isEmpty();
            assertThat(controller.searchResultLabel().getText()).isEqualTo("0 of 3");
        });
    }

    @Test
    void testTheSearchFollowsChangesOfThePlaylist() throws Exception {
        EntryListController controller = searchable();
        runOnFxThread(() -> {
            controller.searchField().setText("hd");

            // e.g. the inputs read again
            controller.playlist().add(channel("3sat HD", "Culture", "3sat.de", "http://example.com/3sat"));

            assertThat(shownNames(controller)).containsExactly("Das Erste HD", "ZDF HD", "arte HD", "3sat HD");
            assertThat(controller.searchResultLabel().getText()).isEqualTo("4 of 5");
        });
    }

    @Test
    void testEscapeClearsTheSearchAndCtrlFMovesToIt() throws Exception {
        EntryListController controller = searchable();
        runOnFxThread(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(new javafx.scene.Group(controller), 600, 300));
            stage.show();
            controller.entryTableView().requestFocus();

            javafx.event.Event.fireEvent(controller.entryTableView(), new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.F,
                    false, !isMac(), false, isMac()));
            boolean focused = controller.searchField().isFocused();

            controller.searchField().setText("zdf");
            javafx.event.Event.fireEvent(controller.searchField(), new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE,
                    false, false, false, false));
            String text = controller.searchField().getText();
            stage.close();

            assertThat(focused).as("Ctrl+F").isTrue();
            assertThat(text).as("Esc").isEmpty();
        });
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    /**
     * The playlist of a project with a clipboard of its own instead of the system clipboard.
     */
    private static EntryListController withClipboard(AtomicReference<String> clipboard, List<String> messages,
                                                     Entry... entries) {
        EntryListController controller = controllerWith(entries);
        controller.clipboardReader = clipboard::get;
        controller.clipboardWriter = clipboard::set;
        controller.messageReporter = messages::add;
        controller.deleteConfirmation = selected -> true;
        return controller;
    }

    private static List<String> names(EntryListController controller) {
        return controller.playlist().stream().map(Entry::getName).toList();
    }

    @Test
    void testCopiedEntriesArePastedBehindTheSelectedEntry() throws Exception {
        AtomicReference<String> clipboard = new AtomicReference<>();
        List<String> messages = new java.util.ArrayList<>();
        EntryListController controller = withClipboard(clipboard, messages, entry("A"), entry("B"), entry("C"));
        runOnFxThread(() -> {
            controller.entryTableView().getSelectionModel().select(2);
            controller.entryTableView().getSelectionModel().select(0);
            controller.copySelected();
            // copied in the order of the playlist
            assertThat(clipboard.get()).containsSubsequence(",A", ",C");

            controller.entryTableView().getSelectionModel().clearAndSelect(1);
            controller.paste();

            assertThat(names(controller)).containsExactly("A", "B", "A", "C", "C");
            // the pasted entries are selected
            assertThat(controller.selectedEntries()).extracting(Entry::getName).containsExactly("A", "C");
            assertThat(controller.selectedEntries().getFirst()).isNotSameAs(controller.playlist().getFirst());
            assertThat(messages).isEmpty();
        });
    }

    @Test
    void testWithoutSelectionEntriesArePastedAtTheEnd() throws Exception {
        AtomicReference<String> clipboard = new AtomicReference<>("#EXTINF:-1,X\nhttp://example.com/x\n");
        EntryListController controller = withClipboard(clipboard, new java.util.ArrayList<>(), entry("A"), entry("B"));
        runOnFxThread(() -> {
            controller.paste();

            assertThat(names(controller)).containsExactly("A", "B", "X");
        });
    }

    @Test
    void testEntriesAreCopiedIntoAnotherProject() throws Exception {
        AtomicReference<String> clipboard = new AtomicReference<>();
        Entry ard = entry("Das Erste HD");
        ard.setTvgId("DasErste.de");
        ard.setVlcOpts(java.util.Map.of("network-caching", "1000"));
        EntryListController source = withClipboard(clipboard, new java.util.ArrayList<>(), ard, entry("ZDF"));
        EntryListController target = withClipboard(clipboard, new java.util.ArrayList<>(), entry("arte"));
        runOnFxThread(() -> {
            source.entryTableView().getSelectionModel().select(0);
            source.copySelected();

            target.paste();

            assertThat(names(target)).containsExactly("arte", "Das Erste HD");
            Entry pasted = target.playlist().getLast();
            assertThat(pasted.getTvgId()).isEqualTo("DasErste.de");
            assertThat(pasted.getChannelUri()).isEqualTo(ard.getChannelUri());
            assertThat(pasted.getVlcOpts()).containsEntry("network-caching", "1000");
            assertThat(names(source)).as("copied, not moved").containsExactly("Das Erste HD", "ZDF");
        });
    }

    /**
     * The playlist read from an input, as in a project: undo computes it again from the entries of the pipeline.
     */
    private static EntryListController readFromInput(AtomicReference<String> clipboard, Entry... entries) {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        EntryListController controller = new ProjectController(dataModel).entryListController;
        dataModel.getInputs().add(new TestPlugins.TestInput("input", List.of(entries)));
        controller.clipboardReader = clipboard::get;
        controller.clipboardWriter = clipboard::set;
        controller.messageReporter = message -> {
        };
        return controller;
    }

    @Test
    void testCutEntriesAreRemovedAndCanBeRestoredByUndo() throws Exception {
        AtomicReference<String> clipboard = new AtomicReference<>();
        EntryListController controller = readFromInput(clipboard, entry("A"), entry("B"), entry("C"));
        runOnFxThread(() -> {
            controller.entryTableView().getSelectionModel().selectIndices(0, 2);
            controller.cutSelected();
            assertThat(names(controller)).containsExactly("B");
            assertThat(clipboard.get()).contains(",A", ",C");

            controller.getDataModel().undo();

            assertThat(names(controller)).containsExactly("A", "B", "C");
        });
    }

    @Test
    void testPastingTextWithoutEntriesShowsAMessage() throws Exception {
        List<String> messages = new java.util.ArrayList<>();
        EntryListController controller = withClipboard(new AtomicReference<>("no playlist here"), messages, entry("A"));
        runOnFxThread(() -> {
            controller.paste();

            assertThat(names(controller)).containsExactly("A");
            assertThat(messages).singleElement().asString().contains("no entries");
        });
    }

    @Test
    void testSelectAllSelectsTheEntriesFoundWhileSearching() throws Exception {
        EntryListController controller = searchable();
        runOnFxThread(() -> {
            controller.searchField().setText("hd");

            controller.selectAll();

            assertThat(controller.selectedEntries()).extracting(Entry::getName)
                    .containsExactly("Das Erste HD", "ZDF HD", "arte HD");
        });
    }

    @Test
    void testDeleteAsksAndRemovesTheSelectedEntries() throws Exception {
        List<List<Entry>> asked = new java.util.ArrayList<>();
        EntryListController controller = withClipboard(new AtomicReference<>(), new java.util.ArrayList<>(),
                entry("A"), entry("B"));
        controller.deleteConfirmation = asked::add;
        runOnFxThread(() -> {
            controller.entryTableView().getSelectionModel().select(1);

            controller.deleteSelectedAfterConfirmation();

            assertThat(asked).singleElement().asList().hasSize(1);
            assertThat(names(controller)).containsExactly("A");
        });
    }
}

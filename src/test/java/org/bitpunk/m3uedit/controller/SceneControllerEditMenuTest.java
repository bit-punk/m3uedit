package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bitpunk.m3uedit.controller.TestPlugins.entry;

class SceneControllerEditMenuTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
        Platform.setImplicitExit(false);
    }

    private interface FxAction<T> {
        T run(SceneController controller, Map<String, MenuItem> items, Stage stage) throws Exception;
    }

    /**
     * Shows the main window and runs the action on the JavaFX thread.
     */
    private static <T> T inWindow(FxAction<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                        SceneControllerEditMenuTest.class.getClassLoader().getResource("fxml/Scene.fxml")));
                Scene scene = loader.load();
                stage.setScene(scene);
                stage.show();
                Menu edit = ((MenuBar) scene.lookup(".menu-bar")).getMenus().stream()
                        .filter(menu -> "Edit".equals(menu.getText())).findFirst().orElseThrow();
                Map<String, MenuItem> items = edit.getItems().stream().filter(item -> item.getText() != null)
                        .collect(Collectors.toMap(MenuItem::getText, Function.identity()));
                result.complete(action.run(loader.getController(), items, stage));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            } finally {
                stage.close();
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    /**
     * The tab pane adds the content of a new tab with the next layout.
     */
    private static void showTabContent(Stage stage) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private static DataModel projectReading(Entry... entries) {
        DataModel dataModel = DataModelFactory.createDataModel("tv");
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        dataModel.getInputs().add(new TestPlugins.TestInput("input", List.of(entries)));
        return dataModel;
    }

    @Test
    void testTheEditMenuFollowsTheFileMenu() throws Exception {
        List<String> menus = inWindow((controller, items, stage) -> ((MenuBar) stage.getScene().lookup(".menu-bar"))
                .getMenus().stream().map(Menu::getText).toList());

        assertThat(menus).containsExactly("File", "Edit", "Project", "Help");
    }

    @Test
    void testWithoutProjectEverythingIsDisabled() throws Exception {
        Map<String, Boolean> disabled = inWindow((controller, items, stage) -> items.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, item -> item.getValue().isDisable())));

        assertThat(disabled).containsOnlyKeys("Undo", "Redo", "Cut", "Copy", "Paste", "Delete", "Select All",
                "Edit Entry…", "Reset Manual Changes").allSatisfy((name, isDisabled) -> assertThat(isDisabled).isTrue());
    }

    @Test
    void testTheFunctionsOfTheSelectionNeedTheFocusInThePlaylist() throws Exception {
        List<Boolean> state = inWindow((controller, items, stage) -> {
            controller.addNewProjectTab(projectReading(entry("A"), entry("B")));
            showTabContent(stage);
            EntryListController playlist = controller.selectedPlaylist();
            playlist.entryTableView().getSelectionModel().select(0);

            playlist.entryTableView().requestFocus();
            boolean deleteInPlaylist = items.get("Delete").isDisable();
            boolean copyInPlaylist = items.get("Copy").isDisable();

            // e.g. a filter of the pipeline: its Del key must not delete entries of the playlist
            stage.getScene().lookup(".pipeline").requestFocus();
            stage.getScene().lookup(".plugin-row").requestFocus();
            return List.of(deleteInPlaylist, copyInPlaylist, items.get("Delete").isDisable(),
                    items.get("Select All").isDisable());
        });

        assertThat(state).containsExactly(false, false, true, true);
    }

    @Test
    void testUndoAndRedoOfTheMenu() throws Exception {
        List<List<String>> playlists = inWindow((controller, items, stage) -> {
            DataModel project = projectReading(entry("A"), entry("B"));
            controller.addNewProjectTab(project);
            assertThat(items.get("Undo").isDisable()).isTrue();

            project.addEntry(entry("C"));
            assertThat(items.get("Undo").isDisable()).isFalse();
            controller.handleUndoAction();
            List<String> undone = project.getSortedEntryList().stream().map(Entry::getName).toList();
            assertThat(items.get("Redo").isDisable()).isFalse();
            controller.handleRedoAction();
            List<String> redone = project.getSortedEntryList().stream().map(Entry::getName).toList();
            return List.of(undone, redone);
        });

        assertThat(playlists).containsExactly(List.of("A", "B"), List.of("A", "B", "C"));
    }

    @Test
    void testInATextFieldTheFunctionsWorkOnItsText() throws Exception {
        List<Object> state = inWindow((controller, items, stage) -> {
            controller.addNewProjectTab(projectReading(entry("A"), entry("B")));
            showTabContent(stage);
            EntryListController playlist = controller.selectedPlaylist();
            playlist.searchField().setText("abc");
            playlist.searchField().requestFocus();

            controller.handleSelectAllAction();
            controller.handleEditEntryAction();
            return List.of(playlist.searchField().getSelectedText(), playlist.selectedEntries().size());
        });

        // the text is selected, not the entries; Enter opens no dialog
        assertThat(state).containsExactly("abc", 0);
    }
}

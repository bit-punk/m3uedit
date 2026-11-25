package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToolBar;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.api.Entry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bitpunk.m3uedit.controller.TestPlugins.entry;

class SceneControllerToolBarTest {

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
        T run(SceneController controller, Stage stage) throws Exception;
    }

    private static <T> T inWindow(FxAction<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loader = org.bitpunk.m3uedit.i18n.Messages.fxmlLoader(Objects.requireNonNull(
                        SceneControllerToolBarTest.class.getClassLoader().getResource("fxml/Scene.fxml")));
                Scene scene = loader.load();
                stage.setScene(scene);
                stage.show();
                result.complete(action.run(loader.getController(), stage));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            } finally {
                stage.close();
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static ButtonBase button(Stage stage, String id) {
        return (ButtonBase) stage.getScene().lookup("#" + id);
    }

    /**
     * The tooltips of the buttons in the order of the toolbar, null for a separator.
     */
    private static List<String> tooltips(Stage stage) {
        ToolBar toolBar = (ToolBar) stage.getScene().lookup(".main-toolbar");
        return toolBar.getItems().stream()
                .map(item -> item instanceof ButtonBase button ? button.getTooltip().getText() : "|").toList();
    }

    private static Map<String, Boolean> disabled(Stage stage, String... ids) {
        Map<String, Boolean> disabled = new LinkedHashMap<>();
        for (String id : ids) {
            disabled.put(id, button(stage, id).isDisable());
        }
        return disabled;
    }

    private static DataModel projectReading(Entry... entries) {
        DataModel dataModel = DataModelFactory.createDataModel("tv");
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        dataModel.getInputs().add(new TestPlugins.TestInput("input", List.of(entries)));
        return dataModel;
    }

    @Test
    void testTheToolbarShowsTheFunctionsInGroups() throws Exception {
        List<String> tooltips = inWindow((controller, stage) -> tooltips(stage));

        assertThat(tooltips).containsExactly("New Project (Ctrl+N)",
                "Open Project (recently used projects in the list) (Ctrl+O)", "Save Project (Ctrl+S)", "|",
                "Undo (Ctrl+Z)", "Redo (Ctrl+Y)", "|", "Reload Inputs (F5)", "Write Playlist", "|", "Add Entry",
                "Play the selected entry", "|", "Programme Guide");
    }

    @Test
    void testWithoutProjectOnlyTheProjectsCanBeCreatedAndOpened() throws Exception {
        Map<String, Boolean> disabled = inWindow((controller, stage) -> disabled(stage, "newProjectToolButton",
                "openProjectToolButton", "saveProjectToolButton", "undoToolButton", "redoToolButton",
                "reloadInputsToolButton", "writePlaylistToolButton", "addEntryToolButton", "playToolButton",
                "programmeGuideToolButton"));

        assertThat(disabled).containsExactly(Map.entry("newProjectToolButton", false),
                Map.entry("openProjectToolButton", false), Map.entry("saveProjectToolButton", true),
                Map.entry("undoToolButton", true), Map.entry("redoToolButton", true),
                Map.entry("reloadInputsToolButton", true), Map.entry("writePlaylistToolButton", true),
                Map.entry("addEntryToolButton", true), Map.entry("playToolButton", true),
                Map.entry("programmeGuideToolButton", true));
    }

    @Test
    void testTheButtonsFollowTheStateOfTheSelectedProject() throws Exception {
        List<Map<String, Boolean>> states = inWindow((controller, stage) -> {
            DataModel project = projectReading(entry("A"), entry("B"));
            controller.addNewProjectTab(project);
            String[] ids = {"saveProjectToolButton", "undoToolButton", "reloadInputsToolButton",
                    "writePlaylistToolButton", "addEntryToolButton", "playToolButton"};
            Map<String, Boolean> opened = disabled(stage, ids);

            project.addEntry(entry("C"));
            controller.selectedPlaylist().entryTableView().getSelectionModel().select(0);
            Map<String, Boolean> changed = disabled(stage, ids);
            return List.of(opened, changed);
        });

        // no output yet: the playlist cannot be written
        assertThat(states.get(0)).containsExactly(Map.entry("saveProjectToolButton", false),
                Map.entry("undoToolButton", true), Map.entry("reloadInputsToolButton", false),
                Map.entry("writePlaylistToolButton", true), Map.entry("addEntryToolButton", false),
                Map.entry("playToolButton", true));
        assertThat(states.get(1)).containsEntry("undoToolButton", false).containsEntry("playToolButton", false);
    }

    @Test
    void testTheUndoButtonUndoesTheLastChange() throws Exception {
        List<String> playlist = inWindow((controller, stage) -> {
            DataModel project = projectReading(entry("A"), entry("B"));
            controller.addNewProjectTab(project);
            project.addEntry(entry("C"));

            button(stage, "undoToolButton").fire();

            return project.getSortedEntryList().stream().map(Entry::getName).toList();
        });

        assertThat(playlist).containsExactly("A", "B");
    }

    @Test
    void testTheButtonsTakeNoFocus() throws Exception {
        List<Boolean> focusTraversable = inWindow((controller, stage) -> ((ToolBar) stage.getScene()
                .lookup(".main-toolbar")).getItems().stream().filter(ButtonBase.class::isInstance)
                .map(Node::isFocusTraversable).distinct().toList());

        // so the Edit menu keeps working on the playlist after clicking a button
        assertThat(focusTraversable).containsExactly(false);
    }
}

package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SceneControllerCloseTabTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private interface FxAction {
        void run() throws Exception;
    }

    private void runOnFxThread(FxAction action) throws Exception {
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

    private record Fixture(SceneController controller, TabPane tabPane, MenuItem saveMenuItem,
                           MenuItem closeMenuItem, AtomicInteger confirmationCalls) {
    }

    private Fixture loadScene(boolean userConfirmsClose) throws Exception {
        FXMLLoader loader = org.bitpunk.m3uedit.i18n.Messages.fxmlLoader(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
        Scene scene = loader.load();
        SceneController controller = loader.getController();
        AtomicInteger confirmationCalls = new AtomicInteger();
        controller.closeConfirmation = name -> {
            confirmationCalls.incrementAndGet();
            return userConfirmsClose;
        };
        TabPane tabPane = (TabPane) scene.lookup("#tabPane");
        MenuBar menuBar = (MenuBar) scene.lookup(".menu-bar");
        MenuItem saveMenuItem = findMenuItem(menuBar, "saveProjectMenuItem");
        MenuItem closeMenuItem = findMenuItem(menuBar, "closeProjectMenuItem");
        return new Fixture(controller, tabPane, saveMenuItem, closeMenuItem, confirmationCalls);
    }

    private static MenuItem findMenuItem(MenuBar menuBar, String id) {
        return menuBar.getMenus().stream()
                .flatMap(menu -> menu.getItems().stream())
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow();
    }

    private static DataModel createDataModel(String name, boolean changed) {
        DataModel dataModel = DataModelFactory.createDataModel(name);
        dataModel.setChanged(changed);
        return dataModel;
    }

    /**
     * Simulates a click on the close-button of a tab, the same way the TabPane skin does it.
     *
     * @return true if the close-request was NOT vetoed (the TabPane would remove the tab)
     */
    private static boolean fireCloseRequest(Tab tab) {
        Event event = new Event(tab, tab, Tab.TAB_CLOSE_REQUEST_EVENT);
        Event.fireEvent(tab, event);
        return !event.isConsumed();
    }

    @Test
    void unchangedProjectIsClosedWithoutConfirmationViaMenu() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("unchanged", false));
            assertThat(f.saveMenuItem().isDisable()).isFalse();

            f.controller().handleCloseProjectAction();

            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(f.confirmationCalls().get()).isZero();
            assertThat(f.saveMenuItem().isDisable()).isTrue();
            assertThat(f.closeMenuItem().isDisable()).isTrue();
        });
    }

    @Test
    void unchangedProjectIsClosedWithoutConfirmationViaCloseButton() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("unchanged", false));
            Tab tab = f.tabPane().getTabs().getFirst();

            assertThat(fireCloseRequest(tab)).isTrue();
            assertThat(f.confirmationCalls().get()).isZero();

            // the TabPane removes the tab after a non-vetoed request
            f.tabPane().getTabs().remove(tab);
            assertThat(f.saveMenuItem().isDisable()).isTrue();
        });
    }

    @Test
    void changedProjectStaysOpenWhenUserCancelsViaCloseButton() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("changed", true));
            Tab tab = f.tabPane().getTabs().getFirst();

            assertThat(fireCloseRequest(tab)).isFalse();
            assertThat(f.confirmationCalls().get()).isEqualTo(1);
            assertThat(f.tabPane().getTabs()).containsExactly(tab);
            assertThat(f.saveMenuItem().isDisable()).isFalse();
        });
    }

    @Test
    void changedProjectStaysOpenWhenUserCancelsViaMenu() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("changed", true));

            f.controller().handleCloseProjectAction();

            assertThat(f.confirmationCalls().get()).isEqualTo(1);
            assertThat(f.tabPane().getTabs()).hasSize(1);
            assertThat(f.saveMenuItem().isDisable()).isFalse();
        });
    }

    @Test
    void changedProjectIsClosedWhenUserConfirms() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(true);
            f.controller().addNewProjectTab(createDataModel("changed", true));

            f.controller().handleCloseProjectAction();

            assertThat(f.confirmationCalls().get()).isEqualTo(1);
            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(f.saveMenuItem().isDisable()).isTrue();
        });
    }

    @Test
    void closeMenuWithoutOpenProjectDoesNothing() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(true);

            f.controller().handleCloseProjectAction();

            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(f.confirmationCalls().get()).isZero();
        });
    }

    @Test
    void testUnsavedChangesAreMarkedInTheTab() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            // a new project has never been saved
            DataModel dataModel = DataModelFactory.createDataModel("tv");
            f.controller().addNewProjectTab(dataModel);
            Tab tab = f.tabPane().getTabs().getFirst();
            assertThat(tab.getText()).isEqualTo("tv *");

            f.controller().projectSaver = model -> ProjectPersisterUtil.SaveResult.SAVED;
            f.controller().handleSaveProjectAction();
            assertThat(tab.getText()).isEqualTo("tv");

            // e.g. a filter enabled
            dataModel.getFilters().getFirst().getEnabled().set(!dataModel.getFilters().getFirst().getEnabled().get());
            assertThat(tab.getText()).isEqualTo("tv *");

            f.controller().projectSaver = model -> ProjectPersisterUtil.SaveResult.CANCELLED;
            f.controller().handleSaveProjectAction();
            assertThat(tab.getText()).as("not saved").isEqualTo("tv *");
        });
    }

    @Test
    void testAnOpenedProjectIsNotMarked() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            DataModel loaded = createDataModel("opened", true);
            loaded.setBackgroundExecutor(task -> {
            });
            f.controller().projectFileChooser = () -> Path.of("/tmp/opened.m3uproj");
            f.controller().projectLoader = path -> loaded;

            f.controller().handleOpenProjectAction();

            assertThat(f.tabPane().getTabs()).singleElement().extracting(Tab::getText).isEqualTo("opened");
        });
    }

    @Test
    void testSuccessfulSaveClearsChangedFlag() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            DataModel dataModel = createDataModel("changed", true);
            f.controller().addNewProjectTab(dataModel);
            f.controller().projectSaver = model -> ProjectPersisterUtil.SaveResult.SAVED;

            f.controller().handleSaveProjectAction();

            assertThat(dataModel.isChanged()).isFalse();
            // closing does not ask anymore
            f.controller().handleCloseProjectAction();
            assertThat(f.confirmationCalls().get()).isZero();
            assertThat(f.tabPane().getTabs()).isEmpty();
        });
    }

    @Test
    void testFailedSaveKeepsChangedFlagAndReportsError() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            DataModel dataModel = createDataModel("broken", true);
            f.controller().addNewProjectTab(dataModel);
            List<String> errors = new ArrayList<>();
            f.controller().errorReporter = errors::add;
            f.controller().projectSaver = model -> ProjectPersisterUtil.SaveResult.FAILED;

            f.controller().handleSaveProjectAction();

            assertThat(dataModel.isChanged()).isTrue();
            assertThat(errors).singleElement().asString().contains("'broken' could not be saved");
        });
    }

    @Test
    void testCancelledSaveKeepsChangedFlag() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            DataModel dataModel = createDataModel("changed", true);
            f.controller().addNewProjectTab(dataModel);
            List<String> errors = new ArrayList<>();
            f.controller().errorReporter = errors::add;
            f.controller().projectSaver = model -> ProjectPersisterUtil.SaveResult.CANCELLED;

            f.controller().handleSaveProjectAction();

            assertThat(dataModel.isChanged()).isTrue();
            assertThat(errors).isEmpty();
        });
    }

    @Test
    void testExitWithoutUnsavedProjectsDoesNotAsk() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            AtomicInteger exitConfirmations = new AtomicInteger();
            f.controller().exitConfirmation = names -> {
                exitConfirmations.incrementAndGet();
                return false;
            };
            assertThat(f.controller().canApplicationBeClosed()).isTrue();

            f.controller().addNewProjectTab(createDataModel("saved", false));
            assertThat(f.controller().canApplicationBeClosed()).isTrue();
            assertThat(exitConfirmations).hasValue(0);
        });
    }

    @Test
    void testExitWithUnsavedProjectsAsksAndRespectsAnswer() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("saved", false));
            f.controller().addNewProjectTab(createDataModel("first", true));
            f.controller().addNewProjectTab(createDataModel("second", true));
            List<List<String>> askedFor = new ArrayList<>();

            f.controller().exitConfirmation = names -> askedFor.add(names) && false;
            assertThat(f.controller().canApplicationBeClosed()).isFalse();

            f.controller().exitConfirmation = names -> askedFor.add(names);
            assertThat(f.controller().canApplicationBeClosed()).isTrue();

            assertThat(askedFor).hasSize(2).allSatisfy(names -> assertThat(names).containsExactly("first", "second"));
        });
    }

    @Test
    void testSaveAndSaveAsUseTheirSavers() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("project", true));
            List<String> used = new ArrayList<>();
            f.controller().projectSaver = model -> {
                used.add("save");
                return ProjectPersisterUtil.SaveResult.SAVED;
            };
            f.controller().projectSaverAs = model -> {
                used.add("save as");
                return ProjectPersisterUtil.SaveResult.SAVED;
            };

            f.controller().handleSaveProjectAction();
            f.controller().handleSaveProjectAsAction();

            assertThat(used).containsExactly("save", "save as");
        });
    }

    @Test
    void testProjectsWithTheSameNameAreIndependent() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("same", false));
            f.controller().addNewProjectTab(createDataModel("same", false));
            assertThat(f.tabPane().getTabs()).hasSize(2);

            f.tabPane().getSelectionModel().select(0);
            f.controller().handleCloseProjectAction();

            assertThat(f.tabPane().getTabs()).hasSize(1);
            // the remaining project still enables the menu
            assertThat(f.saveMenuItem().isDisable()).isFalse();
        });
    }

    @Test
    void testOpenProjectLoadsAndShowsIt() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            Path projectFile = Path.of("/tmp/opened.m3uproj");
            DataModel loaded = createDataModel("opened", true);
            loaded.setBackgroundExecutor(task -> {
            });
            loaded.setProjectFile(projectFile);
            f.controller().projectFileChooser = () -> projectFile;
            f.controller().projectLoader = path -> loaded;

            f.controller().handleOpenProjectAction();

            assertThat(f.tabPane().getTabs()).singleElement().extracting(Tab::getText).isEqualTo("opened");
            assertThat(loaded.isChanged()).isFalse();
            assertThat(loaded.isBusy()).as("inputs are reloaded").isTrue();
        });
    }

    @Test
    void testOpeningAnAlreadyOpenProjectSelectsItsTab() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            DataModel open = createDataModel("open", false);
            open.setProjectFile(Path.of("/tmp/projects/open.m3uproj"));
            f.controller().addNewProjectTab(open);
            f.controller().addNewProjectTab(createDataModel("other", false));
            f.tabPane().getSelectionModel().select(1);
            List<Path> loadedPaths = new ArrayList<>();
            f.controller().projectFileChooser = () -> Path.of("/tmp/projects/../projects/open.m3uproj");
            f.controller().projectLoader = path -> {
                loadedPaths.add(path);
                return null;
            };

            f.controller().handleOpenProjectAction();

            assertThat(loadedPaths).isEmpty();
            assertThat(f.tabPane().getTabs()).hasSize(2);
            assertThat(f.tabPane().getSelectionModel().getSelectedIndex()).isZero();
        });
    }

    @Test
    void testProjectThatCannotBeLoadedIsReported() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            List<String> errors = new ArrayList<>();
            f.controller().errorReporter = errors::add;
            f.controller().projectFileChooser = () -> Path.of("/tmp/corrupt.m3uproj");
            f.controller().projectLoader = path -> null;

            f.controller().handleOpenProjectAction();

            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(errors).singleElement().asString().contains("corrupt.m3uproj").contains("could not be opened");
        });
    }

    @Test
    void testCancelledOpenDoesNothing() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            List<String> errors = new ArrayList<>();
            f.controller().errorReporter = errors::add;
            f.controller().projectFileChooser = () -> null;

            f.controller().handleOpenProjectAction();

            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(errors).isEmpty();
        });
    }
}

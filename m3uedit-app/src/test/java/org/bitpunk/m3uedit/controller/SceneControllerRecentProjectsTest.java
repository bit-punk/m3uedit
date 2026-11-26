package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.bitpunk.m3uedit.config.RecentProject;
import org.bitpunk.m3uedit.config.RecentProjects;
import org.bitpunk.m3uedit.config.RecentProjectsConfig;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SceneControllerRecentProjectsTest {

    @TempDir
    Path tempDir;

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

    private record Fixture(SceneController controller, RecentProjects recentProjects, Menu menu,
                           TableView<RecentProject> table, VBox box, TabPane tabPane, List<String> errors) {
        List<String> menuTexts() {
            return menu.getItems().stream().map(MenuItem::getText).toList();
        }
    }

    @SuppressWarnings("unchecked")
    private Fixture loadScene() throws Exception {
        FXMLLoader loader = org.bitpunk.m3uedit.i18n.Messages.fxmlLoader(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
        Scene scene = loader.load();
        SceneController controller = loader.getController();
        RecentProjects recentProjects = new RecentProjects(new YamlConfigManager<>(tempDir.resolve("recent.yaml"),
                RecentProjectsConfig.class, RecentProjectsConfig::new), Clock.systemUTC());
        controller.setRecentProjects(recentProjects);
        controller.settings = new org.bitpunk.m3uedit.config.Settings(tempDir.resolve("settings.yaml"));
        List<String> errors = new ArrayList<>();
        controller.errorReporter = errors::add;
        Menu menu = (Menu) ((MenuBar) scene.lookup(".menu-bar")).getMenus().getFirst().getItems().stream()
                .filter(item -> "recentProjectsMenu".equals(item.getId())).findFirst().orElseThrow();
        return new Fixture(controller, recentProjects, menu, (TableView<RecentProject>) scene.lookup("#recentProjectsTable"),
                (VBox) scene.lookup("#recentProjectsBox"), (TabPane) scene.lookup("#tabPane"), errors);
    }

    private Path projectFile(String name) throws Exception {
        return Files.writeString(tempDir.resolve(name + ".m3uproj"), "name: " + name);
    }

    private static DataModel loaded(String name) {
        DataModel dataModel = DataModelFactory.createDataModel(name);
        dataModel.setBackgroundExecutor(task -> {
        });
        return dataModel;
    }

    @Test
    void testWithoutRecentProjectsTheTableIsHiddenAndTheMenuDisabled() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();

            assertThat(f.box().isVisible()).isFalse();
            assertThat(f.menu().isDisable()).isTrue();
        });
    }

    @Test
    void testOpenedProjectsAreListedInTheMenuAndOnTheStartScreen() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            Path first = projectFile("first");
            Path second = projectFile("second");
            f.controller().projectLoader = path -> loaded(path.getFileName().toString());

            f.controller().openProject(first);
            f.controller().openProject(second);

            assertThat(f.table().getItems()).extracting(RecentProject::name).containsExactly("second", "first");
            assertThat(f.box().isVisible()).isTrue();
            assertThat(f.menu().isDisable()).isFalse();
            assertThat(f.menuTexts()).containsExactly("second  –  " + tempDir, "first  –  " + tempDir, null, "Clear List");
        });
    }

    @Test
    void testARecentProjectIsOpenedFromTheMenu() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            Path file = projectFile("recent");
            f.recentProjects().add(file);
            List<Path> loadedPaths = new ArrayList<>();
            f.controller().projectLoader = path -> {
                loadedPaths.add(path);
                return loaded("recent");
            };

            f.menu().getItems().getFirst().fire();

            assertThat(loadedPaths).containsExactly(file.toAbsolutePath().normalize());
            assertThat(f.tabPane().getTabs()).singleElement().extracting(Tab::getText).isEqualTo("recent");
        });
    }

    @Test
    void testAMissingProjectFileIsRemovedFromTheList() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            Path missing = tempDir.resolve("gone.m3uproj");
            f.recentProjects().add(missing);

            f.controller().openRecentProject(f.recentProjects().getProjects().getFirst());

            assertThat(f.errors()).singleElement().asString().contains("gone.m3uproj").contains("no longer exists");
            assertThat(f.table().getItems()).isEmpty();
            assertThat(f.tabPane().getTabs()).isEmpty();
        });
    }

    @Test
    void testASavedProjectIsListed() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            DataModel dataModel = DataModelFactory.createDataModel("saved");
            f.controller().addNewProjectTab(dataModel);
            Path file = tempDir.resolve("saved.m3uproj");
            f.controller().projectSaver = model -> {
                model.setProjectFile(file);
                return ProjectPersisterUtil.SaveResult.SAVED;
            };

            f.controller().handleSaveProjectAction();

            assertThat(f.table().getItems()).extracting(RecentProject::name).containsExactly("saved");
        });
    }

    @Test
    void testTheListIsCleared() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            f.recentProjects().add(projectFile("a"));

            f.menu().getItems().getLast().fire();

            assertThat(f.table().getItems()).isEmpty();
            assertThat(f.menu().isDisable()).isTrue();
        });
    }

    @Test
    void testTheOpenButtonOfTheToolbarListsTheRecentProjects() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            // the items of a toolbar not shown are no children yet, so they are not found by lookup
            javafx.scene.control.SplitMenuButton open = ((javafx.scene.control.ToolBar) f.tabPane().getScene()
                    .lookup(".main-toolbar")).getItems().stream()
                    .filter(item -> "openProjectToolButton".equals(item.getId()))
                    .map(javafx.scene.control.SplitMenuButton.class::cast).findFirst().orElseThrow();
            Path first = projectFile("first");
            Path second = projectFile("second");
            List<Path> opened = new ArrayList<>();
            f.controller().projectLoader = path -> {
                opened.add(path);
                return loaded(path.getFileName().toString());
            };
            f.controller().openProject(first);
            f.controller().openProject(second);

            assertThat(open.getItems()).extracting(MenuItem::getText)
                    .containsExactly("second  –  " + tempDir, "first  –  " + tempDir);

            open.getItems().getLast().fire();

            assertThat(opened).containsExactly(first, second, first);
        });
    }

    @Test
    void testTheOpenProjectsAreReopenedAtTheNextStart() throws Exception {
        Path tv = projectFile("tv");
        Path radio = projectFile("radio");
        Path deleted = projectFile("deleted");
        runOnFxThread(() -> {
            Fixture f = loadScene();
            f.controller().settings.setStartup(org.bitpunk.m3uedit.config.StartupMode.REOPEN_PROJECTS);
            f.controller().projectLoader = path -> {
                DataModel dataModel = loaded(path.getFileName().toString());
                dataModel.setProjectFile(path);
                return dataModel;
            };
            f.controller().openProject(tv);
            f.controller().openProject(deleted);
            f.controller().openProject(radio);
            // never saved: no file to reopen
            f.controller().addNewProjectTab(loaded("new"));
            f.controller().exitConfirmation = unsaved -> true;

            assertThat(f.controller().canApplicationBeClosed()).isTrue();
        });
        Files.delete(deleted);

        runOnFxThread(() -> {
            Fixture f = loadScene();
            List<Path> opened = new ArrayList<>();
            f.controller().projectLoader = path -> {
                opened.add(path);
                return loaded(path.getFileName().toString());
            };

            f.controller().reopenProjects();

            assertThat(opened).containsExactly(tv.toAbsolutePath().normalize(), radio.toAbsolutePath().normalize());
            assertThat(f.tabPane().getTabs()).hasSize(2);
            assertThat(f.errors()).isEmpty();
        });
    }

    @Test
    void testWithTheStartScreenNoProjectIsReopened() throws Exception {
        Path tv = projectFile("tv");
        runOnFxThread(() -> {
            Fixture f = loadScene();
            f.controller().projectLoader = path -> {
                DataModel dataModel = loaded(path.getFileName().toString());
                dataModel.setProjectFile(path);
                return dataModel;
            };
            f.controller().openProject(tv);
            f.controller().canApplicationBeClosed();
        });

        runOnFxThread(() -> {
            Fixture f = loadScene();
            f.controller().reopenProjects();

            assertThat(f.tabPane().getTabs()).isEmpty();
        });
    }

    @Test
    void testTheConfigurationChangesTheRecentProjectsAtOnce() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            f.controller().projectLoader = path -> loaded(path.getFileName().toString());
            f.controller().openProject(projectFile("first"));
            f.controller().openProject(projectFile("second"));
            f.controller().configurationDialog = configuration -> {
                org.bitpunk.m3uedit.config.Configuration.Values values = configuration.load();
                return java.util.Optional.of(new ConfigurationDialog.Result(
                        new org.bitpunk.m3uedit.config.Configuration.Values(values.playerCommand(),
                                values.playerArguments(), values.passVlcOptions(), 1, values.startup(),
                                values.language(), values.theme()),
                        java.util.Set.of()));
            };

            f.controller().handleConfigurationAction();

            assertThat(f.table().getItems()).extracting(RecentProject::name).containsExactly("second");
            assertThat(f.menuTexts()).containsExactly("second  –  " + tempDir, null, "Clear List");
        });
    }

    @Test
    void testAChangedThemeIsAppliedWithOk() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene();
            f.controller().configurationDialog = configuration -> {
                org.bitpunk.m3uedit.config.Configuration.Values values = configuration.load();
                return java.util.Optional.of(new ConfigurationDialog.Result(
                        new org.bitpunk.m3uedit.config.Configuration.Values(values.playerCommand(),
                                values.playerArguments(), values.passVlcOptions(), values.maxRecentProjects(),
                                values.startup(), values.language(), org.bitpunk.m3uedit.config.Theme.DARK),
                        java.util.Set.of()));
            };
            try {
                f.controller().handleConfigurationAction();

                assertThat(org.bitpunk.m3uedit.util.Themes.getTheme()).isEqualTo(org.bitpunk.m3uedit.config.Theme.DARK);
                assertThat(f.controller().settings.getTheme()).isEqualTo(org.bitpunk.m3uedit.config.Theme.DARK);
            } finally {
                org.bitpunk.m3uedit.util.Themes.setTheme(org.bitpunk.m3uedit.config.Theme.LIGHT);
            }
        });
    }

    @Test
    void testOpeningAProjectWithPluginsThatAreNotInstalledTellsWhichAreMissing() throws Exception {
        Path project = Files.writeString(tempDir.resolve("other.m3uproj"), """
                name: other computer
                filters:
                  - type: de.example.RadioFilter
                    enabled: true
                """);
        runOnFxThread(() -> {
            Fixture f = loadScene();
            List<String> messages = new ArrayList<>();
            f.controller().messageReporter = messages::add;
            f.controller().projectLoader = path -> {
                DataModel dataModel = org.bitpunk.m3uedit.util.ProjectPersisterUtil.loadProject(path);
                dataModel.setBackgroundExecutor(task -> {
                });
                return dataModel;
            };

            f.controller().openProject(project);

            assertThat(f.tabPane().getTabs()).hasSize(1);
            assertThat(f.errors()).isEmpty();
            assertThat(messages).singleElement().asString()
                    .contains("other computer", "de.example.RadioFilter", "Help → Plugins");
        });
    }
}

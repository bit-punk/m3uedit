package org.bitpunk.m3uedit.controller;

import lombok.extern.slf4j.Slf4j;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.bitpunk.m3uedit.config.RecentProject;
import org.bitpunk.m3uedit.config.RecentProjects;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.bitpunk.m3uedit.util.SvgUtil;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Slf4j
public class SceneController implements Initializable {
    private static final String NEW_PROJECT_ICON_RESOURCE = "/icons/new_project.svg";
    private static final String OPEN_PROJECT_ICON_RESOURCE = "/icons/open_project.svg";
    private static final String ABOUT_ICON_RESOURCE = "/icons/about.svg";
    private static final DateTimeFormatter LAST_OPENED = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Asks the user whether the project with the given name may be closed despite unsaved changes.
     */
    Predicate<String> closeConfirmation = DialogUtil::canProjectBeClosed;
    /**
     * Asks the user whether the application may be closed although the given projects contain unsaved changes.
     */
    Predicate<List<String>> exitConfirmation = DialogUtil::canApplicationBeClosed;
    /**
     * Saves the given project to its project-file, asking for a file for new projects ("save").
     */
    Function<DataModel, ProjectPersisterUtil.SaveResult> projectSaver = ProjectPersisterUtil::saveProject;
    /**
     * Saves the given project, always asking the user for the file to save to ("save as").
     */
    Function<DataModel, ProjectPersisterUtil.SaveResult> projectSaverAs = ProjectPersisterUtil::openSaveDialogAndSaveProject;
    /**
     * Lets the user choose a project-file to open, returns null if cancelled.
     */
    Supplier<Path> projectFileChooser = ProjectPersisterUtil::openProjectFileDialog;
    /**
     * Loads a project-file, returns null if it cannot be loaded.
     */
    Function<Path, DataModel> projectLoader = ProjectPersisterUtil::loadProject;
    /**
     * Shows an error message to the user.
     */
    Consumer<String> errorReporter = DialogUtil::showError;
    /**
     * The recently used project-files, shown in the file-menu and on the start screen.
     */
    private RecentProjects recentProjects;
    @FXML
    private Label statusLabel;
    @FXML
    private Menu recentProjectsMenu;
    @FXML
    private VBox recentProjectsBox;
    @FXML
    private TableView<RecentProject> recentProjectsTable;
    @FXML
    private TableColumn<RecentProject, String> recentProjectNameColumn;
    @FXML
    private TableColumn<RecentProject, String> recentProjectFolderColumn;
    @FXML
    private TableColumn<RecentProject, Instant> recentProjectLastOpenedColumn;
    @FXML
    private MenuItem saveProjectMenuItem;
    @FXML
    private MenuItem saveProjectAsMenuItem;
    @FXML
    private MenuItem closeProjectMenuItem;
    @FXML
    private TabPane tabPane;
    @FXML
    private VBox startScreen;
    @FXML
    private SVGPath newProjectIcon;
    @FXML
    private SVGPath openProjectIcon;
    @FXML
    private SVGPath aboutIcon;

    @FXML
    void handleExitAction() {
        if (canApplicationBeClosed()) {
            Platform.exit();
        }
    }

    /**
     * Asks for confirmation if any open project contains unsaved changes.
     * Used for the exit menu-item as well as for closing the main window.
     *
     * @return true if the application may be closed
     */
    public boolean canApplicationBeClosed() {
        List<String> unsavedProjects = tabPane.getTabs().stream()
                .map(Tab::getContent)
                .filter(ProjectController.class::isInstance)
                .map(content -> ((ProjectController) content).getDataModel())
                .filter(DataModel::isChanged)
                .map(DataModel::getName)
                .toList();
        return unsavedProjects.isEmpty() || exitConfirmation.test(unsavedProjects);
    }

    @FXML
    private void handleAboutAction() {
        var aboutDialogController = new AboutDialogController<>();
        aboutDialogController.show();
    }

    @FXML
    private void handleNewProjectAction() {
        String newProjectName = DialogUtil.getNewProjectName();
        addNewProjectTab(DataModelFactory.createDataModel(newProjectName));
    }

    void addNewProjectTab(DataModel dataModel) {
        ProjectController projectController = getProjectController(dataModel);
        String projectName = dataModel.getName();
        if (isNotEmpty(projectName)) {
            Tab newProjectTab = new Tab(projectName);
            newProjectTab.setContent(projectController);
            newProjectTab.setOnCloseRequest(this::handleTabCloseRequest);
            tabPane.getTabs().add(newProjectTab);
        }
    }

    /**
     * Handles a close-request triggered by the close-button of a project tab.
     * The tab itself is removed by the TabPane afterwards, unless the event gets consumed.
     */
    private void handleTabCloseRequest(Event event) {
        if (!canTabBeClosed((Tab) event.getSource())) {
            event.consume();    // veto: keep the tab open
        }
    }

    /**
     * Closes the given project tab after asking the user for confirmation in case of unsaved changes.
     *
     * @return true if the tab has been closed
     */
    boolean closeProjectTab(Tab tab) {
        if (tab == null || !canTabBeClosed(tab)) {
            return false;
        }
        tabPane.getTabs().remove(tab);
        return true;
    }

    private boolean canTabBeClosed(Tab tab) {
        DataModel dataModel = ((ProjectController) tab.getContent()).getDataModel();
        return !dataModel.isChanged() || closeConfirmation.test(dataModel.getName());
    }

    private ProjectController getProjectController(DataModel dataModel) {
        ProjectController projectController = new ProjectController(dataModel);
        projectController.setStatusLabel(statusLabel);
        return projectController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initIcons();
        initMenuItemDisableListeners();
        initTabPane();
        initStartScreen();
        initRecentProjectsTable();
        setRecentProjects(new RecentProjects());
    }

    /**
     * Shows the given recently used project-files in the file-menu and on the start screen.
     */
    void setRecentProjects(RecentProjects recentProjects) {
        this.recentProjects = recentProjects;
        recentProjectsTable.setItems(recentProjects.getProjects());
        recentProjectsBox.visibleProperty().bind(Bindings.isNotEmpty(recentProjects.getProjects()));
        recentProjectsBox.managedProperty().bind(recentProjectsBox.visibleProperty());
        recentProjects.getProjects().addListener((ListChangeListener<RecentProject>) change -> updateRecentProjectsMenu());
        updateRecentProjectsMenu();
    }

    private void initRecentProjectsTable() {
        recentProjectNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        recentProjectFolderColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().folder()));
        recentProjectLastOpenedColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().lastOpened()));
        recentProjectLastOpenedColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Instant lastOpened, boolean empty) {
                super.updateItem(lastOpened, empty);
                setText(empty || lastOpened == null ? null : LAST_OPENED.format(lastOpened.atZone(ZoneId.systemDefault())));
            }
        });
        recentProjectsTable.setRowFactory(table -> {
            TableRow<RecentProject> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openRecentProject(row.getItem());
                }
            });
            return row;
        });
        recentProjectsTable.setOnKeyPressed(event -> {
            RecentProject selected = recentProjectsTable.getSelectionModel().getSelectedItem();
            if (event.getCode() == KeyCode.ENTER && selected != null) {
                openRecentProject(selected);
                event.consume();
            }
        });
    }

    private void updateRecentProjectsMenu() {
        recentProjectsMenu.getItems().clear();
        for (RecentProject project : recentProjects.getProjects()) {
            MenuItem item = new MenuItem("%s  –  %s".formatted(project.name(), project.folder()));
            item.setMnemonicParsing(false);
            item.setOnAction(event -> openRecentProject(project));
            recentProjectsMenu.getItems().add(item);
        }
        if (!recentProjectsMenu.getItems().isEmpty()) {
            MenuItem clear = new MenuItem("Clear List");
            clear.setOnAction(event -> recentProjects.clear());
            recentProjectsMenu.getItems().addAll(new SeparatorMenuItem(), clear);
        }
        recentProjectsMenu.setDisable(recentProjectsMenu.getItems().isEmpty());
    }

    /**
     * Opens a recently used project-file; one that no longer exists is removed from the list.
     */
    void openRecentProject(RecentProject project) {
        Path projectFile = project.file();
        if (!Files.isRegularFile(projectFile)) {
            recentProjects.remove(projectFile);
            errorReporter.accept("Project-file '%s' no longer exists, it has been removed from the recent projects."
                    .formatted(projectFile));
            return;
        }
        openProject(projectFile);
    }

    private void initIcons() {
        if (newProjectIcon != null) {
            newProjectIcon.setContent(SvgUtil.loadSvgPath(NEW_PROJECT_ICON_RESOURCE));
        }
        if (openProjectIcon != null) {
            openProjectIcon.setContent(SvgUtil.loadSvgPath(OPEN_PROJECT_ICON_RESOURCE));
        }
        if (aboutIcon != null) {
            aboutIcon.setContent(SvgUtil.loadSvgPath(ABOUT_ICON_RESOURCE));
        }
    }

    private void initStartScreen() {
        if (startScreen != null) {
            startScreen.visibleProperty().bind(Bindings.isEmpty(tabPane.getTabs()));
            startScreen.managedProperty().bind(startScreen.visibleProperty());
            tabPane.visibleProperty().bind(Bindings.isNotEmpty(tabPane.getTabs()));
            tabPane.managedProperty().bind(tabPane.visibleProperty());
        }
    }

    private void initMenuItemDisableListeners() {
        BooleanBinding noProjectOpen = Bindings.isEmpty(tabPane.getTabs());
        saveProjectMenuItem.disableProperty().bind(noProjectOpen);
        saveProjectAsMenuItem.disableProperty().bind(noProjectOpen);
        closeProjectMenuItem.disableProperty().bind(noProjectOpen);
    }

    private void initTabPane() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((ov, t, t1) -> updateStatusLabel(t1));
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            if (tabPane.getTabs().isEmpty()) {
                statusLabel.setText("");
                statusLabel.setGraphic(null);
            }
        });
    }

    private void updateStatusLabel(Tab selectedTab) {
        if (selectedTab != null && selectedTab.getContent() instanceof ProjectController projectController) {
            log.debug("changed Tab to: {}", projectController.getDataModel());
            projectController.updateStatusLabel();
        }
    }

    @FXML
    void handleOpenProjectAction() {
        log.debug("Loading new Project ...");
        Path projectFile = projectFileChooser.get();
        if (projectFile != null) {
            openProject(projectFile);
        }
    }

    /**
     * Opens the given project-file in a new tab, or selects its tab if it is already open.
     */
    void openProject(Path projectFile) {
        Optional<Tab> alreadyOpen = findProjectTab(projectFile);
        if (alreadyOpen.isPresent()) {
            log.debug("project-file {} is already open", projectFile);
            tabPane.getSelectionModel().select(alreadyOpen.get());
            recentProjects.add(projectFile);
            return;
        }
        DataModel dataModel = projectLoader.apply(projectFile);
        if (dataModel == null) {
            errorReporter.accept("Project-file '%s' could not be opened, see the log for details.".formatted(projectFile));
            return;
        }
        DataModelFactory.completePlugins(dataModel);    // each filter and sorter is shown exactly once
        dataModel.setChanged(false);    // clear dirty-flag
        addNewProjectTab(dataModel);
        recentProjects.add(projectFile);
        dataModel.reloadInputs();       // entries are not persisted, so they are loaded from the inputs
    }

    private Optional<Tab> findProjectTab(Path projectFile) {
        Path normalized = projectFile.toAbsolutePath().normalize();
        return tabPane.getTabs().stream()
                .filter(tab -> tab.getContent() instanceof ProjectController projectController
                        && projectController.getDataModel().getProjectFile() != null
                        && projectController.getDataModel().getProjectFile().toAbsolutePath().normalize().equals(normalized))
                .findFirst();
    }

    @FXML
    void handleSaveProjectAction() {
        saveSelectedProject(projectSaver);
    }

    @FXML
    void handleSaveProjectAsAction() {
        saveSelectedProject(projectSaverAs);
    }

    private void saveSelectedProject(Function<DataModel, ProjectPersisterUtil.SaveResult> saver) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) {
            return;
        }
        DataModel dataModel = ((ProjectController) selectedTab.getContent()).getDataModel();
        switch (saver.apply(dataModel)) {
            case SAVED -> {
                dataModel.setChanged(false);  // clear dirty-flag
                if (dataModel.getProjectFile() != null) {
                    recentProjects.add(dataModel.getProjectFile());
                }
            }
            case FAILED -> errorReporter.accept("Project '%s' could not be saved, see the log for details.".formatted(dataModel.getName()));
            case CANCELLED -> log.debug("saving project '{}' cancelled", dataModel.getName());
        }
    }

    @FXML
    void handleCloseProjectAction() {
        closeProjectTab(tabPane.getSelectionModel().getSelectedItem());
    }
}
package org.bitpunk.m3uedit.controller;

import lombok.extern.slf4j.Slf4j;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.bitpunk.m3uedit.config.Configuration;
import org.bitpunk.m3uedit.config.Language;
import org.bitpunk.m3uedit.config.RecentProject;
import org.bitpunk.m3uedit.config.RecentProjects;
import org.bitpunk.m3uedit.config.Settings;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.bitpunk.m3uedit.util.PlaylistClipboard;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.bitpunk.m3uedit.util.SvgUtil;
import org.bitpunk.m3uedit.util.Themes;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Objects;
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
    private static final String SAVE_ICON_RESOURCE = "/icons/save.svg";
    private static final String UNDO_ICON_RESOURCE = "/icons/undo.svg";
    private static final String REDO_ICON_RESOURCE = "/icons/redo.svg";
    private static final String RELOAD_ICON_RESOURCE = "/icons/reload.svg";
    private static final String WRITE_PLAYLIST_ICON_RESOURCE = "/icons/write_playlist.svg";
    private static final String ADD_ENTRY_ICON_RESOURCE = "/icons/add_entry.svg";
    private static final String PLAY_ICON_RESOURCE = "/icons/play.svg";
    private static final String PROGRAMME_GUIDE_ICON_RESOURCE = "/icons/programme_guide.svg";

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
     * Shows the configuration dialog, replaced by tests.
     */
    Function<Configuration, Optional<ConfigurationDialog.Result>> configurationDialog =
            configuration -> new ConfigurationDialog(configuration).showAndWait();
    /**
     * The general settings, e.g. whether the open projects are reopened at startup.
     */
    Settings settings = new Settings();
    /**
     * Shows a message to the user, replaced by tests.
     */
    Consumer<String> messageReporter = DialogUtil::showInformation;
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
    private MenuItem newProjectMenuItem;
    @FXML
    private MenuItem openProjectMenuItem;
    @FXML
    private MenuItem reloadInputsMenuItem;
    @FXML
    private MenuItem writePlaylistMenuItem;
    @FXML
    private MenuItem programmeGuideMenuItem;
    @FXML
    private ToolBar toolBar;
    @FXML
    private Button newProjectToolButton;
    @FXML
    private SplitMenuButton openProjectToolButton;
    @FXML
    private Button saveProjectToolButton;
    @FXML
    private Button undoToolButton;
    @FXML
    private Button redoToolButton;
    @FXML
    private Button reloadInputsToolButton;
    @FXML
    private Button writePlaylistToolButton;
    @FXML
    private Button addEntryToolButton;
    @FXML
    private Button playToolButton;
    @FXML
    private Button programmeGuideToolButton;
    @FXML
    private Menu editMenu;
    @FXML
    private MenuItem undoMenuItem;
    @FXML
    private MenuItem redoMenuItem;
    @FXML
    private MenuItem cutMenuItem;
    @FXML
    private MenuItem copyMenuItem;
    @FXML
    private MenuItem pasteMenuItem;
    @FXML
    private MenuItem deleteMenuItem;
    @FXML
    private MenuItem selectAllMenuItem;
    @FXML
    private MenuItem editEntryMenuItem;
    @FXML
    private MenuItem resetManualChangesMenuItem;
    /**
     * Set while the Edit menu is shown and the clipboard contains no entries: Paste is shown disabled then. It is not
     * disabled otherwise, as the shortcut of a disabled menu item does nothing, and the clipboard may change at any
     * time; pasting then tells that there are no entries.
     */
    private final BooleanProperty nothingToPaste = new SimpleBooleanProperty(false);
    /**
     * Set while the focus is in the playlist of the selected project (its table or its search): the functions working
     * on its selection are only enabled then, so e.g. the Del key in the pipeline does not delete entries.
     */
    private final BooleanProperty playlistFocused = new SimpleBooleanProperty(false);
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
        boolean canBeClosed = unsavedProjects.isEmpty() || exitConfirmation.test(unsavedProjects);
        if (canBeClosed) {
            // to reopen them at the next start, if configured
            settings.rememberOpenProjects(openProjectFiles());
        }
        return canBeClosed;
    }

    /**
     * @return the files of the open projects in the order of their tabs; projects never saved have none
     */
    List<Path> openProjectFiles() {
        return tabPane.getTabs().stream()
                .map(Tab::getContent)
                .filter(ProjectController.class::isInstance)
                .map(content -> ((ProjectController) content).getDataModel().getProjectFile())
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Opens the projects that were open when m3uedit was closed, if configured; called once the main window is
     * shown, so dialogs (e.g. for SFTP passwords) appear in front of it. Files that no longer exist are skipped.
     */
    public void reopenProjects() {
        for (Path projectFile : settings.projectsToReopen()) {
            log.info("reopening project {}", projectFile);
            openProject(projectFile);
        }
    }

    @FXML
    void handleConfigurationAction() {
        Configuration configuration = new Configuration(recentProjects, settings);
        Language language = configuration.load().language();
        configurationDialog.apply(configuration).ifPresent(result -> {
            configuration.apply(result.values(), result.toClear()).forEach(errorReporter);
            // all windows change at once
            Themes.setTheme(result.values().theme());
            if (result.values().language() != language) {
                messageReporter.accept(Messages.get("scene.languageChanged"));
            }
        });
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
            Tab newProjectTab = new Tab();
            // unsaved changes are marked by an asterisk after the name
            newProjectTab.textProperty().bind(Bindings.when(dataModel.changedProperty())
                    .then(projectName + " *").otherwise(projectName));
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
        initEditMenu();
        initToolBar();
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
        // in the format of the language of the user interface, e.g. "Nov 21, 2026, 3:04 PM" or "21.11.2026, 15:04"
        DateTimeFormatter lastOpenedFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Messages.locale());
        recentProjectLastOpenedColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Instant lastOpened, boolean empty) {
                super.updateItem(lastOpened, empty);
                setText(empty || lastOpened == null ? null : lastOpenedFormat.format(lastOpened.atZone(ZoneId.systemDefault())));
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
        if (openProjectToolButton != null) {
            openProjectToolButton.getItems().clear();
        }
        for (RecentProject project : recentProjects.getProjects()) {
            MenuItem item = new MenuItem(Messages.get("menu.file.recentProject", project.name(), project.folder()));
            item.setMnemonicParsing(false);
            item.setOnAction(event -> openRecentProject(project));
            recentProjectsMenu.getItems().add(item);
            // the dropdown of the open button of the toolbar lists them too
            if (openProjectToolButton != null) {
                MenuItem toolItem = new MenuItem(item.getText());
                toolItem.setMnemonicParsing(false);
                toolItem.setOnAction(event -> openRecentProject(project));
                openProjectToolButton.getItems().add(toolItem);
            }
        }
        if (!recentProjectsMenu.getItems().isEmpty()) {
            MenuItem clear = new MenuItem(Messages.get("menu.file.clearRecent"));
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
            errorReporter.accept(Messages.get("scene.error.recentProjectMissing", projectFile));
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

    // ---------- Edit menu ----------

    private void initEditMenu() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, tab) -> bindEditMenu());
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> bindEditMenu());
        editMenu.setOnShowing(event -> nothingToPaste.set(focusedText() == null
                && PlaylistClipboard.fromM3u(Clipboard.getSystemClipboard().getString()).isEmpty()));
        editMenu.setOnHidden(event -> nothingToPaste.set(false));
        // the scene of Scene.fxml exists already when this controller is initialized
        if (tabPane.getScene() != null) {
            installOnScene(tabPane.getScene());
        }
        tabPane.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene != null) {
                installOnScene(scene);
            }
        });
        bindEditMenu();
    }

    private void installOnScene(Scene scene) {
        // a second shortcut of Redo, common on Linux and macOS
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), this::handleRedoAction);
        scene.focusOwnerProperty().addListener((focus, oldOwner, owner) -> updatePlaylistFocused());
    }

    /**
     * Enables the items of the Edit menu for the project of the selected tab.
     */
    private void bindEditMenu() {
        updatePlaylistFocused();
        EntryListController playlist = selectedPlaylist();
        if (playlist == null) {
            for (MenuItem item : List.of(undoMenuItem, redoMenuItem, cutMenuItem, copyMenuItem, pasteMenuItem,
                    deleteMenuItem, selectAllMenuItem, editEntryMenuItem, resetManualChangesMenuItem,
                    reloadInputsMenuItem, writePlaylistMenuItem, programmeGuideMenuItem)) {
                item.disableProperty().unbind();
                item.setDisable(true);
            }
            bindToolBar(null);
            return;
        }
        bindProjectMenu(playlist);
        bindToolBar(playlist);
        DataModel dataModel = playlist.getDataModel();
        BooleanBinding noSelection = playlist.noSelectionProperty().or(playlistFocused.not());
        undoMenuItem.disableProperty().bind(dataModel.canUndoProperty().not());
        redoMenuItem.disableProperty().bind(dataModel.canRedoProperty().not());
        cutMenuItem.disableProperty().bind(noSelection);
        copyMenuItem.disableProperty().bind(noSelection);
        pasteMenuItem.disableProperty().bind(nothingToPaste.or(playlistFocused.not()));
        deleteMenuItem.disableProperty().bind(noSelection);
        selectAllMenuItem.disableProperty().bind(playlistFocused.not());
        editEntryMenuItem.disableProperty().bind(noSelection);
        resetManualChangesMenuItem.disableProperty().bind(Bindings.isEmpty(dataModel.getManualChanges()));
    }

    private void updatePlaylistFocused() {
        EntryListController playlist = selectedPlaylist();
        Node owner = tabPane.getScene() == null ? null : tabPane.getScene().getFocusOwner();
        boolean focused = false;
        for (Node node = owner; playlist != null && node != null; node = node.getParent()) {
            if (node == playlist) {
                focused = true;
                break;
            }
        }
        playlistFocused.set(focused);
    }

    // ---------- Project menu and toolbar ----------

    /**
     * Enables the items of the Project menu: under the same conditions as the buttons of the project.
     */
    private void bindProjectMenu(EntryListController playlist) {
        DataModel dataModel = playlist.getDataModel();
        reloadInputsMenuItem.disableProperty().bind(Bindings.isEmpty(dataModel.getInputs())
                .or(dataModel.busyProperty()));
        writePlaylistMenuItem.disableProperty().bind(selectedPipeline().getWriteButton().disableProperty());
        programmeGuideMenuItem.disableProperty().bind(playlist.epgButton().disableProperty());
    }

    private void initToolBar() {
        setUpToolButton(newProjectToolButton, NEW_PROJECT_ICON_RESOURCE, Messages.get("menu.file.newProject"),
                newProjectMenuItem);
        setUpToolButton(openProjectToolButton, OPEN_PROJECT_ICON_RESOURCE, Messages.get("toolbar.openProject"),
                openProjectMenuItem);
        setUpToolButton(saveProjectToolButton, SAVE_ICON_RESOURCE, Messages.get("menu.file.saveProject"),
                saveProjectMenuItem);
        setUpToolButton(undoToolButton, UNDO_ICON_RESOURCE, Messages.get("menu.edit.undo"), undoMenuItem);
        setUpToolButton(redoToolButton, REDO_ICON_RESOURCE, Messages.get("menu.edit.redo"), redoMenuItem);
        setUpToolButton(reloadInputsToolButton, RELOAD_ICON_RESOURCE, Messages.get("menu.project.reloadInputs"),
                reloadInputsMenuItem);
        setUpToolButton(writePlaylistToolButton, WRITE_PLAYLIST_ICON_RESOURCE,
                Messages.get("menu.project.writePlaylist"), writePlaylistMenuItem);
        setUpToolButton(addEntryToolButton, ADD_ENTRY_ICON_RESOURCE, Messages.get("toolbar.addEntry"), null);
        setUpToolButton(playToolButton, PLAY_ICON_RESOURCE, Messages.get("toolbar.play"), null);
        setUpToolButton(programmeGuideToolButton, PROGRAMME_GUIDE_ICON_RESOURCE,
                Messages.get("menu.project.programmeGuide"), programmeGuideMenuItem);
        saveProjectToolButton.disableProperty().bind(saveProjectMenuItem.disableProperty());
        bindToolBar(null);
    }

    /**
     * Shows the icon only, the tooltip names the function and the shortcut of its menu item.
     */
    private static void setUpToolButton(ButtonBase button, String icon, String name, MenuItem menuItem) {
        SVGPath path = new SVGPath();
        path.setContent(SvgUtil.loadSvgPath(icon));
        path.getStyleClass().add("tool-icon");
        button.setGraphic(path);
        button.setText(null);
        button.setFocusTraversable(false);
        KeyCombination shortcut = menuItem == null ? null : menuItem.getAccelerator();
        button.setTooltip(new Tooltip(shortcut == null ? name
                : Messages.get("toolbar.tooltip", name, shortcut.getDisplayText())));
        button.setAccessibleText(name);
    }

    /**
     * Enables the buttons of the toolbar for the project of the selected tab, under the same conditions as the
     * functions they call.
     */
    private void bindToolBar(EntryListController playlist) {
        List<Button> projectButtons = List.of(undoToolButton, redoToolButton, reloadInputsToolButton,
                writePlaylistToolButton, addEntryToolButton, playToolButton, programmeGuideToolButton);
        if (playlist == null) {
            projectButtons.forEach(button -> {
                button.disableProperty().unbind();
                button.setDisable(true);
            });
            return;
        }
        undoToolButton.disableProperty().bind(undoMenuItem.disableProperty());
        redoToolButton.disableProperty().bind(redoMenuItem.disableProperty());
        reloadInputsToolButton.disableProperty().bind(reloadInputsMenuItem.disableProperty());
        writePlaylistToolButton.disableProperty().bind(writePlaylistMenuItem.disableProperty());
        addEntryToolButton.disableProperty().unbind();
        addEntryToolButton.setDisable(false);
        // unlike the Edit menu independent of the focus: clicking the button moves no focus
        playToolButton.disableProperty().bind(playlist.noSelectionProperty());
        programmeGuideToolButton.disableProperty().bind(programmeGuideMenuItem.disableProperty());
    }

    /**
     * @return the pipeline of the project of the selected tab, null if no project is open
     */
    PipelineController selectedPipeline() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab != null && tab.getContent() instanceof ProjectController project ? project.pipelineController : null;
    }

    @FXML
    void handleReloadInputsAction() {
        if (selectedPlaylist() != null) {
            selectedPlaylist().getDataModel().reloadInputs();
        }
    }

    @FXML
    void handleWritePlaylistAction() {
        if (selectedPipeline() != null) {
            // the same as the button below the pipeline, also disabled under the same conditions
            selectedPipeline().getWriteButton().fire();
        }
    }

    @FXML
    void handleAddEntryAction() {
        if (selectedPlaylist() != null) {
            selectedPlaylist().addNewEntry();
        }
    }

    @FXML
    void handlePlayAction() {
        if (selectedPlaylist() != null) {
            selectedPlaylist().playSelected();
        }
    }

    @FXML
    void handleProgrammeGuideAction() {
        if (selectedPlaylist() != null) {
            selectedPlaylist().epgButton().fire();
        }
    }

    /**
     * @return the playlist of the project of the selected tab, null if no project is open
     */
    EntryListController selectedPlaylist() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab != null && tab.getContent() instanceof ProjectController project ? project.entryListController : null;
    }

    /**
     * @return the text field the cursor is in, e.g. the search of the playlist: the editing functions work on its
     * text then, as in any text field
     */
    private TextInputControl focusedText() {
        Scene scene = tabPane.getScene();
        return scene != null && scene.getFocusOwner() instanceof TextInputControl text ? text : null;
    }

    @FXML
    void handleUndoAction() {
        TextInputControl text = focusedText();
        if (text != null) {
            text.undo();
        } else if (selectedPlaylist() != null) {
            selectedPlaylist().getDataModel().undo();
        }
    }

    @FXML
    void handleRedoAction() {
        TextInputControl text = focusedText();
        if (text != null) {
            text.redo();
        } else if (selectedPlaylist() != null) {
            selectedPlaylist().getDataModel().redo();
        }
    }

    @FXML
    void handleCutAction() {
        TextInputControl text = focusedText();
        if (text != null) {
            text.cut();
        } else if (selectedPlaylist() != null) {
            selectedPlaylist().cutSelected();
        }
    }

    @FXML
    void handleCopyAction() {
        TextInputControl text = focusedText();
        if (text != null) {
            text.copy();
        } else if (selectedPlaylist() != null) {
            selectedPlaylist().copySelected();
        }
    }

    @FXML
    void handlePasteAction() {
        TextInputControl text = focusedText();
        if (text != null) {
            text.paste();
        } else if (selectedPlaylist() != null) {
            selectedPlaylist().paste();
        }
    }

    @FXML
    void handleDeleteAction() {
        TextInputControl text = focusedText();
        if (text != null) {
            text.deleteNextChar();
        } else if (selectedPlaylist() != null) {
            selectedPlaylist().deleteSelectedAfterConfirmation();
        }
    }

    @FXML
    void handleSelectAllAction() {
        TextInputControl text = focusedText();
        if (text != null) {
            text.selectAll();
        } else if (selectedPlaylist() != null) {
            selectedPlaylist().selectAll();
        }
    }

    @FXML
    void handleEditEntryAction() {
        // Enter in a text field (e.g. the search) does not open the dialog
        if (focusedText() == null && selectedPlaylist() != null) {
            selectedPlaylist().editSelected();
        }
    }

    @FXML
    void handleResetManualChangesAction() {
        if (selectedPlaylist() != null) {
            selectedPlaylist().resetManualChangesAfterConfirmation();
        }
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
            errorReporter.accept(Messages.get("scene.error.openFailed", projectFile));
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
            case FAILED -> errorReporter.accept(Messages.get("scene.error.saveFailed", dataModel.getName()));
            case CANCELLED -> log.debug("saving project '{}' cancelled", dataModel.getName());
        }
    }

    @FXML
    void handleCloseProjectAction() {
        closeProjectTab(tabPane.getSelectionModel().getSelectedItem());
    }
}
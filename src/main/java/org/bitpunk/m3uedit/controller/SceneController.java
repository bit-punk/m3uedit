package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.bitpunk.m3uedit.util.SvgUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private static final String NEW_PROJECT_ICON_RESOURCE = "/icons/new_project.svg";
    private static final String OPEN_PROJECT_ICON_RESOURCE = "/icons/open_project.svg";
    private static final String ABOUT_ICON_RESOURCE = "/icons/about.svg";

    private final ObservableMap<String, DataModel> dataModelMap = FXCollections.observableMap(new HashMap<>());
    /**
     * Asks the user whether the project with the given name may be closed despite unsaved changes.
     */
    Predicate<String> closeConfirmation = DialogUtil::canProjectBeClosed;
    @FXML
    private Label statusLabel;
    @FXML
    private MenuItem saveProjectMenuItem;
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
    private void handleExitAction() {
        System.exit(0);
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
        dataModelMap.put(dataModel.getName(), dataModel);
        ProjectController projectController = new ProjectController(dataModel);
        projectController.setStatusLabel(statusLabel);
        return projectController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initIcons();
        initMenuItemDisableListeners();
        initDataModelMapListener();
        initTabPane();
        initStartScreen();
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

    /**
     * Initializes a listener for the dataModelMap.
     * This listener checks if the dataModelMap is empty, and if so, sets the text of the statusLabel to an empty string.
     */
    private void initDataModelMapListener() {
        dataModelMap.addListener((MapChangeListener<? super String, ? super DataModel>) change -> {
            if (dataModelMap.isEmpty()) {
                statusLabel.setText("");
            }
        });
    }

    private void initMenuItemDisableListeners() {
        saveProjectMenuItem.disableProperty().bind(Bindings.isEmpty(dataModelMap));
        closeProjectMenuItem.disableProperty().bind(Bindings.isEmpty(dataModelMap));
    }

    private void initTabPane() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((ov, t, t1) -> updateStatusLabel(t1));
        // remove the DataModel of every closed tab, regardless of how the tab got closed
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                for (Tab removedTab : change.getRemoved()) {
                    if (removedTab.getContent() instanceof ProjectController projectController) {
                        DataModel dataModel = projectController.getDataModel();
                        dataModelMap.remove(dataModel.getName(), dataModel);
                    }
                }
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
    private void handleOpenProjectAction() {
        DataModel dataModel;
        log.debug("Loading new Project ...");
        dataModel = ProjectPersisterUtil.openFileDialogAndLoadProject();
        if (dataModel != null) {
            dataModel.setChanged(false);    // clear dirty-flag
            addNewProjectTab(dataModel);
            dataModel.reloadInputs();       // entries are not persisted, so they are loaded from the inputs
        }
    }

    @FXML
    private void handleSaveProjectAction() {
        ProjectPersisterUtil.openSaveDialogAndSaveProject(getCurrentlySelectedProject().getDataModel());
    }

    private ProjectController getCurrentlySelectedProject() {
        return (ProjectController) tabPane.getSelectionModel().getSelectedItem().getContent();
    }

    @FXML
    void handleCloseProjectAction() {
        closeProjectTab(tabPane.getSelectionModel().getSelectedItem());
    }
}
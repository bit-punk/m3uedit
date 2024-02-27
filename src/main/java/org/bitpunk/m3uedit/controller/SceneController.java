package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.ResourceBundle;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private final ObservableMap<String, DataModel> dataModelMap = FXCollections.observableMap(new HashMap<>());
    @FXML
    private Label statusLabel;
    @FXML
    private MenuItem saveProjectMenuItem;
    @FXML
    private MenuItem closeProjectMenuItem;
    @FXML
    private TabPane tabPane;

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

    private void addNewProjectTab(DataModel dataModel) {
        ProjectController projectController = getProjectController(dataModel);
        String projectName = dataModel.getName();
        if (isNotEmpty(projectName)) {
            Tab newProjectTab = new Tab(projectName);
            newProjectTab.setContent(projectController);
            newProjectTab.setOnCloseRequest(this::closeSelectedProjectTab);
            tabPane.getTabs().add(newProjectTab);
        }
    }

    private void closeSelectedProjectTab(Event e) {
        Tab closeTab = (Tab) e.getSource();
        if (canTabBeClosed(closeTab)) {
            TabPane closeTabTabPane = closeTab.getTabPane();
            if (closeTabTabPane != null) {
                closeTabTabPane.getTabs().remove(closeTab);
            }
            dataModelMap.remove(closeTab.getText());
        }
    }

    private boolean canTabBeClosed(Tab closeTab) {
        DataModel dataModel = ((ProjectController) closeTab.getContent()).getDataModel();
        return dataModel.isChanged() && DialogUtil.canProjectBeClosed(dataModel.getName());
    }

    private ProjectController getProjectController(DataModel dataModel) {
        dataModelMap.put(dataModel.getName(), dataModel);
        ProjectController projectController = new ProjectController(dataModel);
        projectController.initModel(dataModel);
        projectController.setStatusLabel(statusLabel);
        return projectController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initMenuItemDisableListeners();
        initDataModelMapListener();
        initTabPane();
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
    }

    private void updateStatusLabel(Tab selectedTab) {
        if (selectedTab != null) {
            ProjectController projectController = (ProjectController) selectedTab.getContent();
            log.debug("changed Tab to: {}", projectController.getDataModel());
            projectController.updateStatusLabel();
        }
    }

    @FXML
    private void handleOpenProjectAction() {
        DataModel dataModel;
        log.debug("Loading new Project ...");
        try {
            dataModel = ProjectPersisterUtil.openFileDialogAndLoadProject();
            dataModel.setChanged(false);    // clear dirty-flag
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        addNewProjectTab(dataModel);
    }

    @FXML
    private void handleSaveProjectAction() {
        ProjectPersisterUtil.openSaveDialogAndSaveProject(getCurrentlySelectedProject().getDataModel());
    }

    private ProjectController getCurrentlySelectedProject() {
        return (ProjectController) tabPane.getSelectionModel().getSelectedItem().getContent();
    }

    @FXML
    private void handleCloseProjectAction() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        Event.fireEvent(tab, new Event(Tab.TAB_CLOSE_REQUEST_EVENT));
    }
}
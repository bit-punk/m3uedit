package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Optional;
import java.util.ResourceBundle;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private final ObservableMap<String, DataModel> dataModelMap = FXCollections.observableMap(new HashMap<>());
    @FXML
    public Scene scene;
    @FXML
    public TabPane tabPane;
    public Label statusLabel;
    public MenuItem saveProjectMenuItem;
    public MenuItem closeProjectMenuItem;

    @FXML
    private void handleExitAction() {
        System.exit(0);
    }

    public void handleAboutAction() {
        var aboutDialogController = new AboutDialogController<>();
        aboutDialogController.show();
    }

    public void handleNewProjectAction() {
        String newProjectName = getNewProjectName();
        addNewProjectTab(newProjectName, new DataModel());
    }

    private void addNewProjectTab(String projectName, DataModel dataModel) {
        ProjectController projectController = getProjectController(projectName, dataModel);
        if (isNotEmpty(projectName)) {
            Tab newProjectTab = new Tab(projectName);
            newProjectTab.setContent(projectController);
            newProjectTab.setOnClosed(this::closeSelectedProjectTab);
            tabPane.getTabs().add(newProjectTab);
        }
    }

    private void closeSelectedProjectTab(Event e) {
        Tab closeTab = (Tab) e.getSource();
        TabPane closeTabTabPane = closeTab.getTabPane();
        if (closeTabTabPane != null) {
            closeTabTabPane.getTabs().remove(closeTab);
        }
        dataModelMap.remove(closeTab.getText());
    }

    private String getNewProjectName() {
        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("Create a new project");
        dialog.setHeaderText("New project name");
        dialog.setContentText("Please enter the name of your new project:");
        Optional<String> result = dialog.showAndWait();
        return result.orElse("");
    }

    private ProjectController getProjectController(String newProjectName, DataModel dataModel) {
        dataModelMap.put(newProjectName, dataModel);
        ProjectController projectController = new ProjectController(dataModel);
        projectController.initModel(dataModel);
        projectController.setStatusLabel(statusLabel);
        return projectController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initMenuItemDisableListeners();
        initTabPane();
    }

    private void initMenuItemDisableListeners() {
        saveProjectMenuItem.disableProperty().bind(Bindings.isEmpty(dataModelMap));
        closeProjectMenuItem.disableProperty().bind(Bindings.isEmpty(dataModelMap));
    }

    private void initTabPane() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((ov, t, t1) -> updateStatusLabel(t1));
    }

    private void updateStatusLabel(Tab t1) {
        log.debug("changed Tab to: {}", t1);
        if (t1 != null) {
            ((ProjectController) t1.getContent()).updateStatusLabel();
        }
    }

    public void handleOpenProjectAction() {
        DataModel dataModel;
        log.debug("Loading new Project ...");
        try {
            dataModel = ProjectPersisterUtil.openFileDialogAndLoadProject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dataModelMap.put(dataModel.getName(), dataModel);
        addNewProjectTab(dataModel.getName(), dataModel);
    }

    public void handleSaveProjectAction() {
        ProjectPersisterUtil.openSaveDialogAndSaveProject(getCurrentlySelectedProject().getDataModel());
    }

    private ProjectController getCurrentlySelectedProject() {
        return (ProjectController) tabPane.getSelectionModel().getSelectedItem().getContent();
    }

    public void handleCloseProjectAction() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        Event.fireEvent(tab, new Event(Tab.CLOSED_EVENT));
    }
}
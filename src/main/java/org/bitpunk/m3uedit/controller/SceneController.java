package org.bitpunk.m3uedit.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import org.bitpunk.m3uedit.model.DataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);

    Map<String, DataModel> dataModelMap = new HashMap<>();
    @FXML
    public Scene scene;
    @FXML
    public TabPane tabPane;

    public Label statusLabel;

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
        if (isNotEmpty(newProjectName)) {
            Tab newProjectTab = new Tab(newProjectName);
            newProjectTab.setContent(addNewProject(newProjectName));
            tabPane.getTabs().add(newProjectTab);
        }
    }

    private String getNewProjectName() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create a new project");
        dialog.setHeaderText("New project name");
        dialog.setContentText("Please enter the name of your new project:");
        Optional<String> result = dialog.showAndWait();
        return result.orElse("");
    }

    private ProjectController addNewProject(String newProjectName) {
        DataModel dataModel = new DataModel();
        dataModelMap.put(newProjectName, dataModel);
        ProjectController projectController = new ProjectController(dataModel);
        projectController.initModel(dataModel);
        projectController.setStatusLabel(statusLabel);
        return projectController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTabPane();
    }

    private void initTabPane() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((ov, t, t1) -> updateStatusLabel(t1));
    }

    private void updateStatusLabel(Tab t1) {
        log.debug("changed Tab to: {}", t1);
        ((ProjectController)t1.getContent()).updateStatusLabel();
    }

}
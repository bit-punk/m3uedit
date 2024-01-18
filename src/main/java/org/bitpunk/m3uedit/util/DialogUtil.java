package org.bitpunk.m3uedit.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class DialogUtil {
    private static final Logger log = LoggerFactory.getLogger(DialogUtil.class);

    private DialogUtil() {
    }

    public static Path getSaveFilePath(String description, String... extension) {
        return showFileChooserDialog(true, description, extension);
    }

    public static Path getOpenFilePath(String description, String... extension) {
        return showFileChooserDialog(false, description, extension);
    }

    private static Path showFileChooserDialog(boolean isSave, String description, String... extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(description, extension));
        File selectedFile;
        selectedFile = isSave ? fileChooser.showSaveDialog(new Stage()) : fileChooser.showOpenDialog(new Stage());
        log.debug("selected open-file: {}", selectedFile);
        return selectedFile == null ? null : selectedFile.toPath();
    }

    public static String getNewProjectName() {
        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("Create a new project");
        dialog.setHeaderText("New project name");
        dialog.setContentText("Please enter the name of your new project:");
        Optional<String> result = dialog.showAndWait();
        return result.orElse("");
    }

    public static boolean canProjectBeClosed(String projectName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Close Project");
        alert.setHeaderText("Project '" + projectName + "' contains unsaved data!");
        alert.setContentText("Do you want to close the project without saving the changed data?");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
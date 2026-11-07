package org.bitpunk.m3uedit.util;

import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for displaying dialog boxes and file choosers.
 */
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
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create a new project");
        dialog.setHeaderText("New project name");
        dialog.setContentText("Please enter the name of your new project:");
        // Access the dialog's OK button
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);

        // Access the text input field
        TextField input = dialog.getEditor();

        // Disable the OK button if the text is empty or contains only whitespace
        okButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> input.getText().trim().isEmpty(),
                        input.textProperty()
                )
        );

        Optional<String> result = dialog.showAndWait();
        return result.map(String::trim).orElse("");
    }

    public static boolean canProjectBeClosed(String projectName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Close Project");
        alert.setHeaderText("Project '" + projectName + "' contains unsaved data!");
        alert.setContentText("Do you want to close the project without saving the changed data?");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Asks whether the application may be closed although the given projects contain unsaved changes.
     *
     * @return true if the user confirmed
     */
    public static boolean canApplicationBeClosed(List<String> unsavedProjectNames) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Exit");
        alert.setHeaderText("There are projects with unsaved changes!");
        alert.setContentText("Unsaved projects: %s%n%nDo you want to exit without saving the changed data?"
                .formatted(String.join(", ", unsavedProjectNames)));
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

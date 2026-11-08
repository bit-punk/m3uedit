package org.bitpunk.m3uedit.util;

import lombok.extern.slf4j.Slf4j;
import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for displaying dialog boxes and file choosers.
 */
@Slf4j
public class DialogUtil {
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
     * Asks whether an existing file may be overwritten.
     *
     * @return true if the user confirmed
     */
    public static boolean canFileBeOverwritten(Path file) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Save Project");
        alert.setHeaderText("The file '" + file.getFileName() + "' already exists!");
        alert.setContentText("Do you want to replace it?%n%n%s".formatted(file.toAbsolutePath()));
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Asks whether the changes the user made to the playlist shall be discarded.
     *
     * @return true if the user confirmed
     */
    public static boolean canManualChangesBeReset(int numberOfChanges) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Changes");
        alert.setHeaderText("Discard %d manual change(s) of the playlist?".formatted(numberOfChanges));
        alert.setContentText("Entries removed, added, edited or moved manually are restored as computed "
                + "from the inputs, filters and sorters.");
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

    /**
     * Shows an error not handled anywhere else.
     */
    public static void showUnexpectedError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("An unexpected error occurred");
        alert.setContentText(message + "\n\nSee the log for details.");
        alert.showAndWait();
    }

    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

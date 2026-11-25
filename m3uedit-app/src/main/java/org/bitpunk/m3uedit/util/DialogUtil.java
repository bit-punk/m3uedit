package org.bitpunk.m3uedit.util;

import lombok.extern.slf4j.Slf4j;
import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.bitpunk.m3uedit.i18n.Messages;

import java.io.File;
import java.nio.file.Files;
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
        return getSaveFilePath(null, description, extension);
    }

    /**
     * @param current the file chosen before, the dialog starts in its directory with its name; null if none
     */
    public static Path getSaveFilePath(Path current, String description, String... extension) {
        return showFileChooserDialog(true, current, description, extension);
    }

    public static Path getOpenFilePath(String description, String... extension) {
        return getOpenFilePath(null, description, extension);
    }

    /**
     * @param current the file chosen before, the dialog starts in its directory with its name; null if none
     */
    public static Path getOpenFilePath(Path current, String description, String... extension) {
        return showFileChooserDialog(false, current, description, extension);
    }

    /**
     * Lets the file chooser start in the directory of the given file, with its name preselected; nothing if the file is
     * null or its directory does not exist (anymore).
     */
    static void startAt(FileChooser fileChooser, Path current) {
        if (current == null) {
            return;
        }
        Path directory = current.toAbsolutePath().getParent();
        if (directory != null && Files.isDirectory(directory)) {
            fileChooser.setInitialDirectory(directory.toFile());
            if (current.getFileName() != null) {
                fileChooser.setInitialFileName(current.getFileName().toString());
            }
        }
    }

    private static Path showFileChooserDialog(boolean isSave, Path current, String description, String... extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(description, extension));
        startAt(fileChooser, current);
        // centered on the window it is opened from
        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        File selectedFile;
        selectedFile = isSave ? fileChooser.showSaveDialog(owner) : fileChooser.showOpenDialog(owner);
        log.debug("selected open-file: {}", selectedFile);
        return selectedFile == null ? null : selectedFile.toPath();
    }

    public static String getNewProjectName() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(Messages.get("dialog.newProject.title"));
        dialog.setHeaderText(Messages.get("dialog.newProject.header"));
        dialog.setContentText(Messages.get("dialog.newProject.content"));
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
        alert.setTitle(Messages.get("dialog.closeProject.title"));
        alert.setHeaderText(Messages.get("dialog.closeProject.header", projectName));
        alert.setContentText(Messages.get("dialog.closeProject.content"));
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
        alert.setTitle(Messages.get("dialog.overwrite.title"));
        alert.setHeaderText(Messages.get("dialog.overwrite.header", file.getFileName()));
        alert.setContentText(Messages.get("dialog.overwrite.content", file.toAbsolutePath()));
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
        alert.setTitle(Messages.get("dialog.resetChanges.title"));
        alert.setHeaderText(Messages.get("dialog.resetChanges.header", numberOfChanges));
        alert.setContentText(Messages.get("dialog.resetChanges.content"));
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
        alert.setTitle(Messages.get("dialog.exit.title"));
        alert.setHeaderText(Messages.get("dialog.exit.header"));
        alert.setContentText(Messages.get("dialog.exit.content", String.join(", ", unsavedProjectNames)));
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Shows an error not handled anywhere else.
     */
    public static void showUnexpectedError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Messages.get("common.error"));
        alert.setHeaderText(Messages.get("error.unexpected.header"));
        alert.setContentText(Messages.get("error.unexpected.content", message));
        alert.showAndWait();
    }

    public static void showInformation(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(Messages.get("common.information"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Messages.get("common.error"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

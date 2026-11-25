package org.bitpunk.m3uedit.plugins.support;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file choosers of the built-in plugins.
 */
public final class FileChoosers {

    private FileChoosers() {
        // Utility class
    }

    /**
     * @param current the file chosen before, the dialog starts in its directory with its name; null if none
     * @return the file chosen, null if cancelled
     */
    public static Path open(Path current, String description, String... extensions) {
        return show(false, current, description, extensions);
    }

    /**
     * @param current the file chosen before, the dialog starts in its directory with its name; null if none
     * @return the file chosen, null if cancelled
     */
    public static Path save(Path current, String description, String... extensions) {
        return show(true, current, description, extensions);
    }

    private static Path show(boolean save, Path current, String description, String... extensions) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extensions));
        startAt(fileChooser, current);
        // centered on the window it is opened from
        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        File selected = save ? fileChooser.showSaveDialog(owner) : fileChooser.showOpenDialog(owner);
        return selected == null ? null : selected.toPath();
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
}

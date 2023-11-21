package org.bitpunk.m3uedit.util;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

public class FileChooserUtil {
    private static final Logger log = LoggerFactory.getLogger(FileChooserUtil.class);

    private FileChooserUtil() {
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
}
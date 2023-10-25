package org.bitpunk.m3uedit.plugins.input;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;

@Data
public class FileInput implements Input {
    private static final Logger log = LoggerFactory.getLogger(FileInput.class);


    private final String name = "File Input";
    private final String description = "Reads a playlist from a local file.";
    private String source = "";

    @Override
    public InputStream read() throws FileNotFoundException {
        return new DataInputStream(new FileInputStream(source));
    }

    @Override
    public void configure() {
        Path outputPath = getOutputPathFromFileDialog();
        if(outputPath != null) {
            this.source = outputPath.toAbsolutePath().toString();
        }
    }

    private Path getOutputPathFromFileDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("M3U-Playlist Files", "*.m3u"));
        File selectedFile;
        selectedFile = fileChooser.showOpenDialog(new Stage());
        log.debug("selected file: {}", selectedFile);
        return selectedFile == null ? null : selectedFile.toPath();
    }
}

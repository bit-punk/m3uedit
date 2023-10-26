package org.bitpunk.m3uedit.plugins.output;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

@Getter
@EqualsAndHashCode
public class FileOutput implements Output {
    private static final Logger log = LoggerFactory.getLogger(FileOutput.class);
    private final String name = "File Output";
    private final String description = "Writes a playlist to a local file.";
    private URI output = null;

    @Override
    public void write(List<Entry> playlist) {
        log.debug("write()");
        if (playlist != null && !playlist.isEmpty() && output != null) {
            log.debug("writing playlist [{}] to file [{}]", playlist, output);
            Writer writer = new Writer();
            try {
                writer.write(playlist, new FileOutputStream(output.getPath()));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void configure() {
        Path outputPath = getOutputPathFromFileDialog();
        if (outputPath != null) {
            this.output = new File(outputPath.toAbsolutePath().toString()).toURI();
        }
    }

    private Path getOutputPathFromFileDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("M3U-Playlist Files", "*.m3u"));
        File selectedFile;
        selectedFile = fileChooser.showSaveDialog(new Stage());
        log.debug("selected file: {}", selectedFile);
        return selectedFile == null ? null : selectedFile.toPath();
    }
}
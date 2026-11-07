package org.bitpunk.m3uedit.plugins.output.file;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = false)
public class FileOutput extends AbstractPlugin implements Output {
    private static final Logger log = LoggerFactory.getLogger(FileOutput.class);
    @XStreamOmitField
    private final String name = "File Output";
    @XStreamOmitField
    private final String description = "Writes a playlist to a local file.";
    @Setter(AccessLevel.PACKAGE)
    private URI output = null;

    @Override
    public void write(List<Entry> playlist) {
        log.debug("write()");
        if (playlist != null && !playlist.isEmpty() && output != null) {
            log.debug("writing playlist [{}] to file [{}]", playlist, output);
            Writer writer = new Writer();
            try (OutputStream outputStream = new FileOutputStream(Path.of(output).toFile())) {
                writer.write(playlist, outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException("Error writing playlist to " + output, e);
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

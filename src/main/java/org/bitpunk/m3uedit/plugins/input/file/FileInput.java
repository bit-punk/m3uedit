package org.bitpunk.m3uedit.plugins.input.file;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

@Getter
@Setter
public class FileInput extends AbstractInput implements Input {
    private static final Logger log = LoggerFactory.getLogger(FileInput.class);

    @XStreamOmitField
    private final String name = "File Input";
    @XStreamOmitField
    private final String description = "Reads a playlist from a local file.";
    private String source;

    @Override
    public List<Entry> read() throws FileNotFoundException {
        try (InputStream inputStream = new FileInputStream(source)) {
            return readData(inputStream);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading playlist from " + source, e);
        }
    }

    @Override
    public void configure() {
        Path inputPath = DialogUtil.getOpenFilePath("M3U-Playlist Files (*.m3u)", "*.m3u");
        if (inputPath != null) {
            this.source = inputPath.toAbsolutePath().toString();
        }
    }

}

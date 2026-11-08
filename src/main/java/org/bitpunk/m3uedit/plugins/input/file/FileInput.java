package org.bitpunk.m3uedit.plugins.input.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.util.DialogUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Slf4j
public class FileInput extends AbstractInput implements Input {
    @JsonIgnore
    private final String name = "File Input";
    @JsonIgnore
    private final String description = "Reads a playlist from a local file.";
    private String source;

    @Override
    public List<Entry> read() throws FileNotFoundException {
        if (source == null || source.isBlank()) {
            // not configured yet: no file has been chosen
            return new ArrayList<>();
        }
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

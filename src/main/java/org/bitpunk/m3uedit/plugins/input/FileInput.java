package org.bitpunk.m3uedit.plugins.input;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FileInput extends AbstractInput implements Input {
    private static final Logger log = LoggerFactory.getLogger(FileInput.class);


    private final String name = "File Input";
    private final String description = "Reads a playlist from a local file.";
    private String source = "";

    @Override
    public List<Entry> read() throws FileNotFoundException {
        return readData(new DataInputStream(new FileInputStream(source)));
    }

    @Override
    public void configure() {
        Path inputPath = DialogUtil.getOpenFilePath("M3U-Playlist Files (*.m3u)", "*.m3u");
        if (inputPath != null) {
            this.source = inputPath.toAbsolutePath().toString();
        }
    }

}

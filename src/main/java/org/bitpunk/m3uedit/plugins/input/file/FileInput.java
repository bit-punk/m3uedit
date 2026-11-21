package org.bitpunk.m3uedit.plugins.input.file;

import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.i18n.Messages;
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
    private String source;

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("input.file.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("input.file.description");
    }

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
            throw new UncheckedIOException(Messages.get("input.file.readError", source), e);
        }
    }

    @Override
    public void configure() {
        // starts at the file chosen before
        Path current = source == null || source.isBlank() ? null : Path.of(source);
        Path inputPath = DialogUtil.getOpenFilePath(current, Messages.get("input.file.filter"), "*.m3u");
        if (inputPath != null) {
            this.source = inputPath.toAbsolutePath().toString();
        }
    }

}

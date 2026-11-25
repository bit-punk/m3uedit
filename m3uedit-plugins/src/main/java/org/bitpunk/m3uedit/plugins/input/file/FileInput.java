package org.bitpunk.m3uedit.plugins.input.file;

import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Playlist;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.support.FileChoosers;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Reads a playlist from a local file.
 */
@PluginType(id = "FileInput", nameKey = "input.file.name", texts = "org.bitpunk.m3uedit.plugins.messages")
public class FileInput implements Input {
    private PluginContext context;
    @Getter
    @Setter
    private String source;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("input.file.name");
    }

    @Override
    public String getDescription() {
        return context.text("input.file.description");
    }

    @Override
    public Class<? extends Record> getSettingsType() {
        return FileInputSettings.class;
    }

    @Override
    public Record getSettings() {
        return new FileInputSettings(source);
    }

    @Override
    public void setSettings(Record settings) {
        source = ((FileInputSettings) settings).source();
    }

    @Override
    public Playlist read() throws IOException {
        if (source == null || source.isBlank()) {
            // not configured yet: no file has been chosen
            return Playlist.empty();
        }
        try (InputStream inputStream = new FileInputStream(source)) {
            return context.readPlaylist(inputStream);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException(context.text("input.file.readError", source), e);
        }
    }

    @Override
    public boolean configure(Window owner) {
        // starts at the file chosen before
        Path current = source == null || source.isBlank() ? null : Path.of(source);
        Path inputPath = FileChoosers.open(current, context.text("input.file.filter"), "*.m3u");
        if (inputPath == null) {
            return false;
        }
        this.source = inputPath.toAbsolutePath().toString();
        return true;
    }

    /**
     * Inputs are compared by identity; only a short description is returned here.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), context == null ? "" : getName());
    }
}

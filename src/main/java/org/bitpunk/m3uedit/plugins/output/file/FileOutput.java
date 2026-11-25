package org.bitpunk.m3uedit.plugins.output.file;

import javafx.stage.Window;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Export;
import org.bitpunk.m3uedit.api.Export.ExportFile;
import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.output.OutputException;
import org.bitpunk.m3uedit.util.DialogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Slf4j
@PluginType(id = "FileOutput", nameKey = "output.file.name", texts = "i18n.messages")
public class FileOutput implements Output {
    @Getter(AccessLevel.NONE)
    private PluginContext context;
    @Setter(AccessLevel.PACKAGE)
    private URI output = null;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("output.file.name");
    }

    @Override
    public String getDescription() {
        return context.text("output.file.description");
    }

    @Override
    public Class<? extends Record> getSettingsType() {
        return FileOutputSettings.class;
    }

    @Override
    public Record getSettings() {
        return new FileOutputSettings(output == null ? null : output.toString());
    }

    @Override
    public void setSettings(Record settings) {
        String uri = settings instanceof FileOutputSettings fileSettings ? fileSettings.output() : null;
        output = uri == null || uri.isBlank() ? null : URI.create(uri);
    }

    @Override
    public void write(Export export) {
        log.debug("write()");
        List<Entry> playlist = export.playlist();
        if (playlist.isEmpty() || output == null) {
            return;
        }
        Path playlistPath = Path.of(output);
        // the additional files are written first, so the playlist never references a file not written
        Map<ExportFile, Path> written = new IdentityHashMap<>();
        for (ExportFile file : export.files()) {
            Path target = playlistPath.resolveSibling(file.fileNameFor(playlistPath.getFileName().toString()));
            // copied as content: a new file gets the default permissions, not those of the (private) temporary file
            try (InputStream content = Files.newInputStream(file.content())) {
                Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
                written.put(file, target);
            } catch (IOException e) {
                throw new OutputException(context.text("output.file.fileWriteError", file.name(), target,
                        e.getMessage()), e);
            }
        }
        log.debug("writing playlist with {} entries to file [{}]", playlist.size(), output);
        try (OutputStream outputStream = new FileOutputStream(playlistPath.toFile())) {
            // players on this machine read the additional files from their location
            context.writePlaylist(playlist, export.headerAttributes(file -> written.containsKey(file)
                    ? written.get(file).toUri().toString() : null), outputStream);
        } catch (IOException e) {
            throw new OutputException(context.text("output.file.writeError", playlistPath, e.getMessage()), e);
        }
    }

    @Override
    public boolean configure(Window owner) {
        Path outputPath = getOutputPathFromFileDialog();
        if (outputPath == null) {
            return false;
        }
        this.output = new File(outputPath.toAbsolutePath().toString()).toURI();
        return true;
    }

    private Path getOutputPathFromFileDialog() {
        // starts at the file written before
        Path current = output == null || !"file".equals(output.getScheme()) ? null : Path.of(output);
        Path selected = DialogUtil.getSaveFilePath(current, context.text("output.file.filter"), "*.m3u");
        log.debug("selected file: {}", selected);
        return selected;
    }

    /**
     * Plugins are compared by identity, so only a short description is returned.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}

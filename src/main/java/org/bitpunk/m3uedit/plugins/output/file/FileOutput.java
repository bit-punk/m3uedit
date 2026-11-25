package org.bitpunk.m3uedit.plugins.output.file;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.PluginType;
import org.bitpunk.m3uedit.plugins.output.Export;
import org.bitpunk.m3uedit.plugins.output.Export.ExportFile;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.OutputException;
import org.bitpunk.m3uedit.util.DialogUtil;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Slf4j
@PluginType(id = "FileOutput", nameKey = "output.file.name")
public class FileOutput extends AbstractPlugin implements Output {
    @Setter(AccessLevel.PACKAGE)
    private URI output = null;

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("output.file.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("output.file.description");
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
                throw new OutputException(Messages.get("output.file.fileWriteError", file.name(), target,
                        e.getMessage()), e);
            }
        }
        log.debug("writing playlist with {} entries to file [{}]", playlist.size(), output);
        Writer writer = new Writer();
        try (OutputStream outputStream = new FileOutputStream(playlistPath.toFile())) {
            // players on this machine read the additional files from their location
            writer.write(playlist, export.headerAttributes(file -> written.containsKey(file)
                    ? written.get(file).toUri().toString() : null), outputStream);
        } catch (IOException e) {
            throw new OutputException(Messages.get("output.file.writeError", playlistPath, e.getMessage()), e);
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
        // starts at the file written before
        Path current = output == null || !"file".equals(output.getScheme()) ? null : Path.of(output);
        Path selected = DialogUtil.getSaveFilePath(current, Messages.get("output.file.filter"), "*.m3u");
        log.debug("selected file: {}", selected);
        return selected;
    }
}

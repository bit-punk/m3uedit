package org.bitpunk.m3uedit.plugins.output.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.extern.slf4j.Slf4j;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.Export;
import org.bitpunk.m3uedit.plugins.output.Export.ExportFile;
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
public class FileOutput extends AbstractPlugin implements Output {
    @JsonIgnore
    private final String name = "File Output";
    @JsonIgnore
    private final String description = "Writes a playlist to a local file.";
    @Setter(AccessLevel.PACKAGE)
    private URI output = null;

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
                throw new OutputException("%s could not be written to %s: %s".formatted(file.name(), target,
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
            throw new OutputException("The playlist could not be written to %s: %s".formatted(
                    playlistPath, e.getMessage()), e);
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
        Path selected = DialogUtil.getSaveFilePath(current, "M3U-Playlist Files", "*.m3u");
        log.debug("selected file: {}", selected);
        return selected;
    }
}

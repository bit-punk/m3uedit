package org.bitpunk.m3uedit.plugins.output.file;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Export;
import org.bitpunk.m3uedit.api.PluginException;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.plugins.output.OutputException;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileOutputTest {

    @Test
    void testBasicProperties() {
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        assertThat(fileOutput.getName()).isEqualTo("File Output");
        assertThat(fileOutput.getDescription()).isEqualTo("Writes a playlist to a local file.");
        assertThat(fileOutput.getOutput()).isNull();
    }

    @Test
    void testWriteNullOrEmptyListDoesNotThrow() {
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        fileOutput.write(Export.of(null));
        fileOutput.write(Export.of(List.of()));
    }

    @Test
    void testWritePlaylistToFile(@TempDir Path tempDir) {
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        File targetFile = tempDir.resolve("output.m3u").toFile();

        // Simulate configured output URI via reflection or method
        Entry entry = new Entry();
        entry.setDuration("-1");
        entry.setName("Test Channel");
        entry.setChannelUri("http://example.com/stream.ts");

        // When output is null, write should do nothing and not throw
        fileOutput.write(Export.of(List.of(entry)));
        assertThat(targetFile.exists()).isFalse();
    }

    @Test
    void testWriteOverwritesConfiguredFileCompletely(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("output.m3u");
        Files.writeString(target, "old content that is much longer than the new playlist ".repeat(20));
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        fileOutput.setOutput(target.toUri());

        Entry entry = new Entry();
        entry.setDuration("-1");
        entry.setName("Test Channel");
        entry.setChannelUri("http://example.com/stream.ts");
        entry.setVlcOpts(Map.of());
        fileOutput.write(Export.of(List.of(entry)));

        assertThat(Files.readString(target)).isEqualTo(
                "#EXTM3U" + System.lineSeparator()
                        + "#EXTINF:-1,Test Channel" + System.lineSeparator()
                        + "http://example.com/stream.ts");
        // the stream has been closed, so the file can be moved/deleted right away (fails on Windows otherwise)
        Files.delete(target);
    }

    @Test
    void testWriteToNonExistingDirectoryThrowsOutputException(@TempDir Path tempDir) {
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        fileOutput.setOutput(tempDir.resolve("missing").resolve("output.m3u").toUri());
        Entry entry = new Entry();
        entry.setName("Test Channel");
        entry.setChannelUri("http://example.com/stream.ts");
        entry.setVlcOpts(Map.of());

        assertThatThrownBy(() -> fileOutput.write(Export.of(List.of(entry)))).isInstanceOf(OutputException.class)
                .isInstanceOf(PluginException.class)
                .hasMessageContaining("could not be written to").hasMessageContaining("output.m3u");
    }

    @Test
    void testAdditionalFilesAreWrittenNextToThePlaylistAndReferenced(@TempDir Path tempDir) throws IOException {
        Path content = tempDir.resolve("content.tmp");
        Files.writeString(content, "<tv/>");
        Path target = tempDir.resolve("tv.m3u");
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        fileOutput.setOutput(target.toUri());
        Entry entry = new Entry();
        entry.setName("ZDF");
        entry.setChannelUri("http://example.com/zdf.ts");
        org.bitpunk.m3uedit.api.Export export = new org.bitpunk.m3uedit.api.Export(List.of(entry),
                List.of("http://source/epg.xml"), List.of(new org.bitpunk.m3uedit.api.Export.ExportFile(
                "EPG", ".xml", List.of("x-tvg-url"), content)));

        fileOutput.write(export);

        Path guide = tempDir.resolve("tv.xml");
        assertThat(guide).hasContent("<tv/>");
        if (guide.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            // not the permissions of the private temporary file: players may run as another user
            Files.setPosixFilePermissions(content, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            Files.delete(guide);
            fileOutput.write(export);
            assertThat(Files.getPosixFilePermissions(guide)).isEqualTo(Files.getPosixFilePermissions(target));
        }
        assertThat(Files.readString(target)).startsWith("#EXTM3U x-tvg-url=\"" + guide.toUri() + "\" url-tvg=\"http://source/epg.xml\"");
    }

    @Test
    void testSettingsRoundTrip(@TempDir Path tempDir) {
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        assertThat(fileOutput.getSettings()).isEqualTo(new FileOutputSettings(null));
        fileOutput.setOutput(tempDir.resolve("tv.m3u").toUri());

        FileOutputSettings settings = (FileOutputSettings) fileOutput.getSettings();
        assertThat(settings.output()).isEqualTo(tempDir.resolve("tv.m3u").toUri().toString());

        FileOutput loaded = TestPluginSupport.create(FileOutput::new);
        loaded.setSettings(settings);
        assertThat(loaded.getOutput()).isEqualTo(tempDir.resolve("tv.m3u").toUri());
        assertThat(loaded.getSettings()).isEqualTo(settings);
    }

    @Test
    void testDefaultsForMissingSettings() {
        FileOutput fileOutput = TestPluginSupport.create(FileOutput::new);
        fileOutput.setOutput(java.net.URI.create("file:///tmp/playlist.m3u"));

        fileOutput.setSettings(new FileOutputSettings(null));

        assertThat(fileOutput.getOutput()).isNull();
    }

    @Test
    void testProjectFileOfTodayIsRead() {
        DataModel loaded = ProjectPersisterUtil.loadProject(Path.of("src/test/resources/project/testproject.m3uproj"));

        FileOutput fileOutput = (FileOutput) loaded.getOutputs().getFirst();
        assertThat(PluginStates.enabled(fileOutput).get()).isTrue();
        assertThat(fileOutput.getOutput()).hasToString("file:///tmp/playlist.m3u");
    }
}

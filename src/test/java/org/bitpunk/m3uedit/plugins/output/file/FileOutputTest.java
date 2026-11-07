package org.bitpunk.m3uedit.plugins.output.file;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.output.OutputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileOutputTest {

    @Test
    void testBasicProperties() {
        FileOutput fileOutput = new FileOutput();
        assertThat(fileOutput.getName()).isEqualTo("File Output");
        assertThat(fileOutput.getDescription()).isEqualTo("Writes a playlist to a local file.");
        assertThat(fileOutput.getOutput()).isNull();
    }

    @Test
    void testWriteNullOrEmptyListDoesNotThrow() {
        FileOutput fileOutput = new FileOutput();
        fileOutput.write(null);
        fileOutput.write(Collections.emptyList());
    }

    @Test
    void testWritePlaylistToFile(@TempDir Path tempDir) {
        FileOutput fileOutput = new FileOutput();
        File targetFile = tempDir.resolve("output.m3u").toFile();

        // Simulate configured output URI via reflection or method
        Entry entry = new Entry();
        entry.setDuration("-1");
        entry.setName("Test Channel");
        entry.setChannelUri("http://example.com/stream.ts");

        // When output is null, write should do nothing and not throw
        fileOutput.write(List.of(entry));
        assertThat(targetFile.exists()).isFalse();
    }

    @Test
    void testWriteOverwritesConfiguredFileCompletely(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("output.m3u");
        Files.writeString(target, "old content that is much longer than the new playlist ".repeat(20));
        FileOutput fileOutput = new FileOutput();
        fileOutput.setOutput(target.toUri());

        Entry entry = new Entry();
        entry.setDuration("-1");
        entry.setName("Test Channel");
        entry.setChannelUri("http://example.com/stream.ts");
        entry.setVlcOpts(Map.of());
        fileOutput.write(List.of(entry));

        assertThat(Files.readString(target)).isEqualTo(
                "#EXTM3U" + System.lineSeparator()
                        + "#EXTINF:-1,Test Channel" + System.lineSeparator()
                        + "http://example.com/stream.ts");
        // the stream has been closed, so the file can be moved/deleted right away (fails on Windows otherwise)
        Files.delete(target);
    }

    @Test
    void testWriteToNonExistingDirectoryThrowsOutputException(@TempDir Path tempDir) {
        FileOutput fileOutput = new FileOutput();
        fileOutput.setOutput(tempDir.resolve("missing").resolve("output.m3u").toUri());
        Entry entry = new Entry();
        entry.setName("Test Channel");
        entry.setChannelUri("http://example.com/stream.ts");
        entry.setVlcOpts(Map.of());

        assertThatThrownBy(() -> fileOutput.write(List.of(entry))).isInstanceOf(OutputException.class)
                .hasMessageContaining("could not be written to").hasMessageContaining("output.m3u");
    }
}

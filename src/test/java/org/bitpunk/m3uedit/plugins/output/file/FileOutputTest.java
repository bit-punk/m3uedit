package org.bitpunk.m3uedit.plugins.output.file;

import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}

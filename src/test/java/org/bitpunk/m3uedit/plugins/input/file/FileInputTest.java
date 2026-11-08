package org.bitpunk.m3uedit.plugins.input.file;

import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileInputTest {

    @Test
    void testBasicProperties() {
        FileInput fileInput = new FileInput();
        assertThat(fileInput.getName()).isEqualTo("File Input");
        assertThat(fileInput.getDescription()).isEqualTo("Reads a playlist from a local file.");
        assertThat(fileInput.getSource()).isNull();
        assertThat(fileInput.getEnabled().get()).isFalse();

        fileInput.getEnabled().set(true);
        assertThat(fileInput.getEnabled().get()).isTrue();
    }

    @Test
    void testReadFromValidM3uFile(@TempDir Path tempDir) throws IOException {
        Path m3uFile = tempDir.resolve("test.m3u");
        String m3uContent = """
                #EXTM3U
                #EXTINF:-1 tvg-id="id1" tvg-name="Channel 1" tvg-logo="http://example.com/logo.png" group-title="News",Channel 1
                http://example.com/stream1.ts
                #EXTINF:-1 tvg-id="id2" tvg-name="Channel 2" tvg-logo="http://example.com/logo2.png" group-title="Sports",Channel 2
                http://example.com/stream2.ts
                """;
        Files.writeString(m3uFile, m3uContent);

        FileInput fileInput = new FileInput();
        fileInput.setSource(m3uFile.toAbsolutePath().toString());

        List<Entry> entries = fileInput.read();

        assertThat(entries).isNotNull().hasSize(2);

        Entry entry1 = entries.getFirst();
        assertThat(entry1.getName()).isEqualTo("Channel 1");
        assertThat(entry1.getChannelUri()).isEqualTo("http://example.com/stream1.ts");
        assertThat(entry1.getTvgId()).isEqualTo("id1");
        assertThat(entry1.getTvgName()).isEqualTo("Channel 1");
        assertThat(entry1.getTvgLogo()).isEqualTo("http://example.com/logo.png");
        assertThat(entry1.getGroupTitle()).isEqualTo("News");

        Entry entry2 = entries.get(1);
        assertThat(entry2.getName()).isEqualTo("Channel 2");
        assertThat(entry2.getChannelUri()).isEqualTo("http://example.com/stream2.ts");
        assertThat(entry2.getTvgId()).isEqualTo("id2");
        assertThat(entry2.getTvgName()).isEqualTo("Channel 2");
        assertThat(entry2.getTvgLogo()).isEqualTo("http://example.com/logo2.png");
        assertThat(entry2.getGroupTitle()).isEqualTo("Sports");
    }

    @Test
    void testReadNonExistentFileThrowsFileNotFoundException(@TempDir Path tempDir) {
        FileInput fileInput = new FileInput();
        fileInput.setSource(tempDir.resolve("does_not_exist.m3u").toAbsolutePath().toString());

        assertThatThrownBy(fileInput::read)
                .isInstanceOf(FileNotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void testReadWithoutSourceReturnsNoEntries(String source) throws IOException {
        FileInput fileInput = new FileInput();
        fileInput.setSource(source);

        assertThat(fileInput.read()).isEmpty();
    }

    @Test
    void testInputsAreComparedByIdentity() {
        FileInput input1 = new FileInput();
        input1.setSource("/path/to/playlist.m3u");
        FileInput input2 = new FileInput();
        input2.setSource("/path/to/playlist.m3u");
        int hashCode = input1.hashCode();

        assertThat(input1).isEqualTo(input1).isNotEqualTo(input2).isNotEqualTo(null);
        input1.setSource("/other/playlist.m3u");
        assertThat(input1.hashCode()).isEqualTo(hashCode);
        assertThat(input1).hasToString("FileInput[File Input]");
    }
}

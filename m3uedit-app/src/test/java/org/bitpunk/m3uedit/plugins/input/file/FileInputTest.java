package org.bitpunk.m3uedit.plugins.input.file;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
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
        FileInput fileInput = TestPluginSupport.create(FileInput::new);
        assertThat(fileInput.getName()).isEqualTo("File Input");
        assertThat(fileInput.getDescription()).isEqualTo("Reads a playlist from a local file.");
        assertThat(fileInput.getSource()).isNull();
        assertThat(PluginStates.enabled(fileInput).get()).isFalse();

        PluginStates.enabled(fileInput).set(true);
        assertThat(PluginStates.enabled(fileInput).get()).isTrue();
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

        FileInput fileInput = TestPluginSupport.create(FileInput::new);
        fileInput.setSource(m3uFile.toAbsolutePath().toString());

        List<Entry> entries = fileInput.read().entries();

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
        FileInput fileInput = TestPluginSupport.create(FileInput::new);
        fileInput.setSource(tempDir.resolve("does_not_exist.m3u").toAbsolutePath().toString());

        assertThatThrownBy(fileInput::read)
                .isInstanceOf(FileNotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void testReadWithoutSourceReturnsNoEntries(String source) throws IOException {
        FileInput fileInput = TestPluginSupport.create(FileInput::new);
        fileInput.setSource(source);

        assertThat(fileInput.read().entries()).isEmpty();
    }

    @Test
    void testInputsAreComparedByIdentity() {
        FileInput input1 = TestPluginSupport.create(FileInput::new);
        input1.setSource("/path/to/playlist.m3u");
        FileInput input2 = TestPluginSupport.create(FileInput::new);
        input2.setSource("/path/to/playlist.m3u");
        int hashCode = input1.hashCode();

        assertThat(input1).isEqualTo(input1).isNotEqualTo(input2).isNotEqualTo(null);
        input1.setSource("/other/playlist.m3u");
        assertThat(input1.hashCode()).isEqualTo(hashCode);
        assertThat(input1).hasToString("FileInput[File Input]");
    }

    @Test
    void testTheSettingsAreRestored() {
        FileInput fileInput = TestPluginSupport.create(FileInput::new);
        fileInput.setSource("/path/to/playlist.m3u");

        FileInput restored = TestPluginSupport.create(FileInput::new);
        restored.setSettings(fileInput.getSettings());

        assertThat(restored.getSettingsType()).isEqualTo(FileInputSettings.class);
        assertThat(restored.getSettings()).isEqualTo(new FileInputSettings("/path/to/playlist.m3u"));
        assertThat(restored.getSource()).isEqualTo("/path/to/playlist.m3u");
    }

    @Test
    void testMissingSettingsAreNotConfigured() throws IOException {
        FileInput fileInput = TestPluginSupport.create(FileInput::new);

        fileInput.setSettings(new FileInputSettings(null));

        assertThat(fileInput.getSource()).isNull();
        assertThat(fileInput.read().entries()).isEmpty();
    }

    @Test
    void testTheSettingsAreStoredInTheProjectFileAsBefore(@TempDir Path tempDir) throws IOException {
        Path projectFile = Files.writeString(tempDir.resolve("file.m3uproj"), """
                ---
                name: file
                inputs:
                  - type: FileInput
                    enabled: true
                    source: /path/to/playlist.m3u
                """);

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        assertThat(loaded.getInputs()).singleElement().isInstanceOf(FileInput.class)
                .extracting(input -> ((FileInput) input).getSource()).isEqualTo("/path/to/playlist.m3u");
        assertThat(ProjectPersisterUtil.persistedState(loaded.getInputs().getFirst()))
                .isEqualTo("---\nsource: /path/to/playlist.m3u\n");
    }
}

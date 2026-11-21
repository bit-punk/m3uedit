package org.bitpunk.m3uedit.util;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DialogUtilTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @Test
    void testTheFileDialogStartsAtTheFileChosenBefore(@TempDir Path dir) throws Exception {
        Path playlist = Files.writeString(dir.resolve("tv.m3u"), "#EXTM3U\n");
        FileChooser fileChooser = new FileChooser();

        DialogUtil.startAt(fileChooser, playlist);

        assertThat(fileChooser.getInitialDirectory()).isEqualTo(dir.toFile());
        assertThat(fileChooser.getInitialFileName()).isEqualTo("tv.m3u");
    }

    @Test
    void testAFileNotWrittenYetIsPreselectedToo(@TempDir Path dir) {
        FileChooser fileChooser = new FileChooser();

        DialogUtil.startAt(fileChooser, dir.resolve("new.m3u"));

        assertThat(fileChooser.getInitialDirectory()).isEqualTo(dir.toFile());
        assertThat(fileChooser.getInitialFileName()).isEqualTo("new.m3u");
    }

    @Test
    void testWithoutFileOrDirectoryTheDialogStartsAsUsual(@TempDir Path dir) {
        FileChooser withoutFile = new FileChooser();
        DialogUtil.startAt(withoutFile, null);
        FileChooser missingDirectory = new FileChooser();
        DialogUtil.startAt(missingDirectory, dir.resolve("deleted").resolve("tv.m3u"));

        assertThat(withoutFile.getInitialDirectory()).isNull();
        assertThat(withoutFile.getInitialFileName()).isNull();
        assertThat(missingDirectory.getInitialDirectory()).isNull();
        assertThat(missingDirectory.getInitialFileName()).isNull();
    }
}

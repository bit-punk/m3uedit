package org.bitpunk.m3uedit.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsTest {

    @TempDir
    Path tempDir;

    private Settings settings() {
        return new Settings(tempDir.resolve(Settings.CONFIG_FILE_NAME));
    }

    private Path project(String name) throws Exception {
        return Files.writeString(tempDir.resolve(name + ".m3uproj"), "name: " + name);
    }

    @Test
    void testTheStartScreenIsShownByDefault() throws Exception {
        assertThat(settings().getStartup()).isEqualTo(StartupMode.START_SCREEN);

        settings().rememberOpenProjects(List.of(project("tv")));

        assertThat(settings().projectsToReopen()).as("only reopened if configured").isEmpty();
    }

    @Test
    void testTheOpenProjectsAreReopenedWithoutTheMissingOnes() throws Exception {
        Path tv = project("tv");
        Path radio = project("radio");
        Path deleted = project("deleted");
        settings().setStartup(StartupMode.REOPEN_PROJECTS);
        settings().rememberOpenProjects(List.of(tv, deleted, radio));
        Files.delete(deleted);

        assertThat(settings().projectsToReopen()).containsExactly(tv, radio);
    }

    @Test
    void testAFileEditedByHandIsRead() throws Exception {
        Path tv = project("tv");
        Files.writeString(tempDir.resolve(Settings.CONFIG_FILE_NAME), """
                startup: REOPEN_PROJECTS
                openProjects:
                  - %s
                """.formatted(tv));

        assertThat(settings().getStartup()).isEqualTo(StartupMode.REOPEN_PROJECTS);
        assertThat(settings().projectsToReopen()).containsExactly(tv);
    }

    @Test
    void testChangingTheStartupKeepsTheOpenProjects() throws Exception {
        Path tv = project("tv");
        settings().rememberOpenProjects(List.of(tv));

        settings().setStartup(StartupMode.REOPEN_PROJECTS);

        assertThat(settings().projectsToReopen()).containsExactly(tv);
    }
}

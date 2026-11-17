package org.bitpunk.m3uedit.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RecentProjectsTest {
    private static final Instant NOW = Instant.parse("2026-11-17T10:15:00Z");

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve(RecentProjects.CONFIG_FILE_NAME);
    }

    private RecentProjects recentProjects() {
        return new RecentProjects(new YamlConfigManager<>(configFile(), RecentProjectsConfig.class, RecentProjectsConfig::new),
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private Path project(String name) {
        return tempDir.resolve("projects").resolve(name + ".m3uproj");
    }

    @Test
    void testTheMostRecentlyUsedProjectIsFirstAndListedOnce() {
        RecentProjects recent = recentProjects();

        recent.add(project("a"));
        recent.add(project("b"));
        recent.add(tempDir.resolve("projects/../projects/a.m3uproj"));

        assertThat(recent.getProjects()).extracting(RecentProject::name).containsExactly("a", "b");
        assertThat(recent.getProjects().getFirst().folder()).isEqualTo(tempDir.resolve("projects").toString());
        assertThat(recent.getProjects().getFirst().lastOpened()).isEqualTo(NOW);
    }

    @Test
    void testTenProjectsAreKeptByDefault() {
        RecentProjects recent = recentProjects();

        for (int i = 1; i <= 12; i++) {
            recent.add(project("p" + i));
        }

        assertThat(recent.getMaxEntries()).isEqualTo(10);
        assertThat(recent.getProjects()).hasSize(10);
        assertThat(recent.getProjects().getFirst().name()).isEqualTo("p12");
        assertThat(recent.getProjects().getLast().name()).isEqualTo("p3");
    }

    @Test
    void testTheListIsStoredAndReadAgain() {
        recentProjects().add(project("a"));
        recentProjects().add(project("b"));

        RecentProjects read = recentProjects();

        assertThat(read.getProjects()).extracting(RecentProject::name).containsExactly("b", "a");
        assertThat(read.getProjects().getFirst().lastOpened()).isEqualTo(NOW);
        assertThat(configFile()).content().contains("maxEntries: 10");
    }

    @Test
    void testTheNumberOfProjectsIsConfigurable() throws Exception {
        Files.writeString(configFile(), """
                maxEntries: 2
                projects:
                  - path: /p/a.m3uproj
                  - path: /p/b.m3uproj
                  - path: /p/c.m3uproj
                """);

        RecentProjects recent = recentProjects();
        assertThat(recent.getProjects()).extracting(RecentProject::name).containsExactly("a", "b");

        recent.add(project("new"));
        assertThat(recent.getProjects()).extracting(RecentProject::name).containsExactly("new", "a");
        assertThat(configFile()).content().contains("maxEntries: 2").doesNotContain("b.m3uproj");
    }

    @Test
    void testZeroTurnsTheListOff() throws Exception {
        Files.writeString(configFile(), "maxEntries: 0\n");

        RecentProjects recent = recentProjects();
        recent.add(project("a"));

        assertThat(recent.getProjects()).isEmpty();
    }

    @Test
    void testChangesOfTheFileAreTakenOverBeforeAdding() throws Exception {
        RecentProjects recent = recentProjects();
        recent.add(project("a"));
        // e.g. another instance of the application
        recentProjects().add(project("b"));

        recent.add(project("c"));

        assertThat(recent.getProjects()).extracting(RecentProject::name).containsExactly("c", "b", "a");
    }

    @Test
    void testProjectsAreRemovedAndCleared() {
        RecentProjects recent = recentProjects();
        recent.add(project("a"));
        recent.add(project("b"));

        recent.remove(project("a"));
        assertThat(recent.getProjects()).extracting(RecentProject::name).containsExactly("b");

        recent.clear();
        assertThat(recent.getProjects()).isEmpty();
        assertThat(recentProjects().getProjects()).isEmpty();
    }
}

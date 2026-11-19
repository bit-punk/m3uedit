package org.bitpunk.m3uedit.cli;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.util.HeadlessMode;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class CommandLineRunnerTest {
    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private Path playlist;
    private Path output;

    @BeforeEach
    void setUp() throws Exception {
        playlist = dir.resolve("in.m3u");
        Files.copy(Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("tvhd.m3u")).toURI()), playlist);
        output = dir.resolve("out.m3u");
    }

    @AfterEach
    void leaveHeadlessMode() {
        HeadlessMode.setActive(false);
    }

    private Path project(String name, Path... inputs) throws Exception {
        StringBuilder yaml = new StringBuilder("---\nname: %s\ninputs:\n".formatted(name));
        for (Path input : inputs) {
            yaml.append("  - type: FileInput\n    source: \"%s\"\n".formatted(input));
        }
        yaml.append("outputs:\n  - type: FileOutput\n    output: %s\n".formatted(output.toUri()));
        return Files.writeString(dir.resolve(name + ".m3uproj"), yaml);
    }

    private int run(CommandLineRunner runner, String... args) throws Exception {
        return runner.run(CommandLineOptions.parse(args));
    }

    private int run(String... args) throws Exception {
        return run(runner(ProjectPersisterUtil::loadProject), args);
    }

    private CommandLineRunner runner(java.util.function.Function<Path, DataModel> loader) {
        return new CommandLineRunner(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), loader);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private static long entries(Path playlist) throws Exception {
        return Files.readAllLines(playlist).stream().filter(line -> line.startsWith("#EXTINF")).count();
    }

    @Test
    void testTheInputsAreReadAndThePlaylistIsWritten() throws Exception {
        int exitCode = run("--run", project("demo", playlist).toString());

        assertThat(exitCode).isEqualTo(CommandLineRunner.OK);
        assertThat(entries(output)).isEqualTo(entries(playlist)).isPositive();
        assertThat(out()).contains("demo", "30 entries read, 30 written to 1 output(s)");
        assertThat(err()).isEmpty();
        assertThat(HeadlessMode.isActive()).as("no dialogs are shown").isTrue();
    }

    @Test
    void testADryRunWritesNothing() throws Exception {
        assertThat(run("-n", "-r", project("demo", playlist).toString())).isEqualTo(CommandLineRunner.OK);

        assertThat(output).doesNotExist();
        assertThat(out()).contains("not written (dry run)");
    }

    @Test
    void testQuietPrintsNothingOnSuccess() throws Exception {
        assertThat(run("-q", "-r", project("demo", playlist).toString())).isEqualTo(CommandLineRunner.OK);

        assertThat(out()).isEmpty();
        assertThat(output).exists();
    }

    @Test
    void testAnInputThatCannotBeReadKeepsThePlaylistWritten() throws Exception {
        Files.writeString(output, "the last good playlist");

        int exitCode = run("-r", project("broken", playlist, dir.resolve("missing.m3u")).toString());

        assertThat(exitCode).isEqualTo(CommandLineRunner.FAILED);
        assertThat(output).hasContent("the last good playlist");
        assertThat(err()).contains("input could not be read", "missing.m3u", "not written, 1 input(s) could not be read");
    }

    @Test
    void testInputErrorsCanBeIgnored() throws Exception {
        int exitCode = run("--ignore-input-errors", "-r", project("broken", playlist, dir.resolve("missing.m3u")).toString());

        assertThat(exitCode).isEqualTo(CommandLineRunner.OK);
        assertThat(entries(output)).isEqualTo(entries(playlist));
        // the error is still reported
        assertThat(err()).contains("input could not be read");
    }

    @Test
    void testNothingIsWrittenWithoutEntries() throws Exception {
        assertThat(run("--ignore-input-errors", "-r", project("none", dir.resolve("missing.m3u")).toString()))
                .isEqualTo(CommandLineRunner.FAILED);
        assertThat(err()).contains("no entries have been read");
        assertThat(output).doesNotExist();
    }

    @Test
    void testAnEmptyPlaylistIsNotWritten() throws Exception {
        Path project = project("filtered", playlist);
        CommandLineRunner runner = runner(file -> {
            DataModel model = ProjectPersisterUtil.loadProject(file);
            model.getFilters().add(new RemoveAllFilter());
            return model;
        });

        assertThat(run(runner, "-r", project.toString())).isEqualTo(CommandLineRunner.FAILED);
        assertThat(err()).contains("the playlist is empty after 30 entries have been read");
        assertThat(output).doesNotExist();
    }

    @Test
    void testAllProjectsAreRunTheWorstExitCodeIsReturned() throws Exception {
        int exitCode = run("-r", dir.resolve("missing.m3uproj").toString(), "-r", project("demo", playlist).toString());

        assertThat(exitCode).isEqualTo(CommandLineRunner.USAGE);
        assertThat(err()).contains("missing.m3uproj: project-file not found");
        assertThat(output).exists();
    }

    @Test
    void testAProjectThatCannotBeLoaded() throws Exception {
        Path broken = Files.writeString(dir.resolve("broken.m3uproj"), "---\nname: broken\ninputs:\n  - type: NoSuchInput\n");

        assertThat(run("-r", broken.toString())).isEqualTo(CommandLineRunner.USAGE);
        assertThat(err()).contains("project-file could not be loaded");
    }

    @Test
    void testAProjectWithoutOutputs() throws Exception {
        Path project = Files.writeString(dir.resolve("nooutput.m3uproj"),
                "---\nname: nooutput\ninputs:\n  - type: FileInput\n    source: \"%s\"\n".formatted(playlist));

        assertThat(run("-r", project.toString())).isEqualTo(CommandLineRunner.FAILED);
        assertThat(err()).contains("the project has no outputs");
        // a dry run needs no outputs
        assertThat(run("-n", "-r", project.toString())).isEqualTo(CommandLineRunner.OK);
    }

    /**
     * Removes all entries.
     */
    private static class RemoveAllFilter implements Filter {
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);

        @Override
        public List<Entry> filter(List<Entry> list) {
            return List.of();
        }

        @Override
        public String describeRemoval(Entry entry, List<Entry> input) {
            return "all removed";
        }

        @Override
        public boolean isConfigurable() {
            return false;
        }

        @Override
        public void configure() {
            // nothing to configure
        }

        @Override
        public String getName() {
            return "Remove All";
        }

        @Override
        public String getDescription() {
            return "removes all entries";
        }

        @Override
        public BooleanProperty getEnabled() {
            return enabled;
        }
    }
}

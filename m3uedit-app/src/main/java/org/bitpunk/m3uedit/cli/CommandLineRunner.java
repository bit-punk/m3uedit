package org.bitpunk.m3uedit.cli;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.Sorter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.PluginJars;
import org.bitpunk.m3uedit.plugins.PluginServices;
import org.bitpunk.m3uedit.plugins.missing.MissingPlugin;
import org.bitpunk.m3uedit.util.HeadlessMode;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs projects without user interface (command-line mode, see {@link CommandLineOptions#usage()}): the inputs are
 * read, the pipeline is computed and the playlist is written to the outputs, all in the calling thread.
 */
@Slf4j
public class CommandLineRunner {
    public static final int OK = 0;
    public static final int FAILED = 1;
    public static final int USAGE = 2;

    private final PrintStream out;
    private final PrintStream err;
    private final Function<Path, DataModel> projectLoader;

    public CommandLineRunner(PrintStream out, PrintStream err) {
        this(out, err, ProjectPersisterUtil::loadProject);
    }

    CommandLineRunner(PrintStream out, PrintStream err, Function<Path, DataModel> projectLoader) {
        this.out = out;
        this.err = err;
        this.projectLoader = projectLoader;
    }

    /**
     * Runs all projects of the options, also after one of them failed.
     *
     * @return the exit code: {@link #OK}, {@link #FAILED} or {@link #USAGE}, the worst of all projects
     */
    public int run(CommandLineOptions options) {
        HeadlessMode.setActive(true);
        // plugin jars that cannot be loaded are skipped; the projects using their plugins fail then
        PluginServices.all();
        PluginJars.installed().getProblems().forEach(problem ->
                err.printf("m3uedit: plugin jar %s not loaded: %s%n", problem.file(), problem.reason()));
        int exitCode = OK;
        for (Path projectFile : options.projects()) {
            exitCode = Math.max(exitCode, run(projectFile, options));
        }
        return exitCode;
    }

    private int run(Path projectFile, CommandLineOptions options) {
        if (!Files.isRegularFile(projectFile)) {
            err.printf("%s: project-file not found%n", projectFile);
            return USAGE;
        }
        DataModel project = projectLoader.apply(projectFile);
        if (project == null) {
            err.printf("%s: project-file could not be loaded, see the log for details%n", projectFile);
            return USAGE;
        }
        String name = "%s (%s)".formatted(project.getName(), projectFile);
        log.info("running project {}", name);
        List<String> problems = new ArrayList<>();
        project.setErrorHandler(problems::add);
        project.setBackgroundExecutor(Runnable::run);
        project.setFxExecutor(Runnable::run);

        // the entries are not stored in the project, so the inputs are read and the pipeline is computed
        // the playlist would not be the one the project describes
        List<MissingPlugin> missing = project.getMissingPlugins();
        if (!missing.isEmpty()) {
            err.printf("%s: plugins not installed: %s, the playlist is not written%n", name,
                    missing.stream().map(MissingPlugin::getTypeId).distinct().collect(Collectors.joining(", ")));
            return FAILED;
        }
        project.reloadInputs();
        List<String> inputErrors = project.getInputErrors();
        inputErrors.forEach(error -> err.printf("%s: input could not be read: %s%n", name, error));
        // other problems, e.g. of enrichers, do not prevent writing the playlist
        if (!options.quiet()) {
            problems.stream().filter(problem -> inputErrors.stream().noneMatch(problem::contains))
                    .forEach(problem -> err.printf("%s: warning: %s%n", name, problem));
        }
        int read = project.getPipelineResult().raw().size();
        int entries = project.getSortedEntryList().size();
        if (!inputErrors.isEmpty() && !options.ignoreInputErrors()) {
            err.printf("%s: not written, %d input(s) could not be read (use --ignore-input-errors to write anyway)%n",
                    name, inputErrors.size());
            return FAILED;
        }
        // a project without inputs is a playlist created by hand; inputs without entries are rather a problem (e.g.
        // a FritzBox switched off), the playlist would be replaced by the entries added by hand only
        if (read == 0 && !project.getInputs().isEmpty()) {
            err.printf("%s: not written, no entries have been read from the inputs%n", name);
            return FAILED;
        }
        if (entries == 0) {
            err.printf("%s: not written, the playlist is empty%s%n", name,
                    read > 0 ? " after %d entries have been read".formatted(read) : "");
            return FAILED;
        }
        if (options.dryRun()) {
            print(options, "%s: %d entries read, %d in the playlist, not written (dry run)", name, read, entries);
            return OK;
        }
        if (project.getOutputs().isEmpty()) {
            err.printf("%s: not written, the project has no outputs%n", name);
            return FAILED;
        }

        problems.clear();
        project.saveEntries();
        if (!problems.isEmpty()) {
            problems.forEach(problem -> err.printf("%s: %s%n", name, problem));
            return FAILED;
        }
        print(options, "%s: %d entries read, %d written to %d output(s)", name, read, entries,
                project.getOutputs().size());
        return OK;
    }

    private void print(CommandLineOptions options, String format, Object... args) {
        if (!options.quiet()) {
            out.println(format.formatted(args));
        }
    }

    /**
     * Prints the plugins (option {@code --list-plugins}): the built-in ones, those installed by the user, and the jars
     * that could not be loaded.
     */
    public static void listPlugins(PrintStream out, List<PluginDescriptor<?>> plugins, PluginJars jars) {
        out.printf("Plugins (built-in, and installed in %s):%n", PluginJars.directory());
        for (PluginDescriptor<?> plugin : plugins) {
            out.printf("  %-9s %-45s %s%s%n", kind(plugin), plugin.id(), plugin.name(),
                    plugin.isInstalled() ? "  (" + plugin.jar().getFileName() + ")" : "");
        }
        List<PluginJars.Problem> problems = jars.getProblems();
        if (!problems.isEmpty()) {
            out.println("Not loaded:");
            problems.forEach(problem -> out.printf("  %s: %s%n", problem.file().getFileName(), problem.reason()));
        }
    }

    private static String kind(PluginDescriptor<?> plugin) {
        return Stream.of(Input.class, Filter.class, Sorter.class, Enricher.class, Output.class)
                .filter(kind -> kind.isAssignableFrom(plugin.type())).map(Class::getSimpleName).findFirst().orElse("");
    }
}

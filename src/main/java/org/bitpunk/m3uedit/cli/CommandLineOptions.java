package org.bitpunk.m3uedit.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.help.TextHelpAppendable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The options of the command-line mode, see {@link #usage()}, parsed by Apache Commons CLI.
 * <p>
 * Parsed before the logging is initialized, so this class must not log.
 *
 * @param projects          the project-files to run, in the order given
 * @param dryRun            runs the pipeline without writing to the outputs
 * @param quiet             only errors are printed
 * @param verbose           the steps are logged in detail
 * @param ignoreInputErrors writes the playlist although some inputs could not be read
 * @param help              only prints the usage
 * @param version           only prints the version
 */
public record CommandLineOptions(List<Path> projects, boolean dryRun, boolean quiet, boolean verbose,
                                 boolean ignoreInputErrors, boolean help, boolean version) {

    private static final Option RUN = Option.builder("r").longOpt("run").hasArg().argName("project-file")
            .desc("the project to run; can be given several times, the projects are run one after another").get();
    private static final Option DRY_RUN = Option.builder("n").longOpt("dry-run")
            .desc("run the pipeline, but do not write to the outputs").get();
    private static final Option IGNORE_INPUT_ERRORS = Option.builder().longOpt("ignore-input-errors")
            .desc("write the playlist although some inputs could not be read (an empty playlist is never written)")
            .get();
    private static final Option QUIET = Option.builder("q").longOpt("quiet").desc("only print errors").get();
    private static final Option VERBOSE = Option.builder("v").longOpt("verbose").desc("print details of the run")
            .get();
    private static final Option HELP = Option.builder("h").longOpt("help").desc("print this help").get();
    private static final Option VERSION = Option.builder().longOpt("version").desc("print the version").get();

    /**
     * The options in the order they are shown by the help.
     */
    private static final List<Option> ALL = List.of(RUN, DRY_RUN, IGNORE_INPUT_ERRORS, QUIET, VERBOSE, HELP, VERSION);

    /**
     * A call with wrong options.
     */
    public static class UsageException extends Exception {
        public UsageException(String message) {
            super(message);
        }
    }

    public CommandLineOptions {
        projects = List.copyOf(projects);
    }

    private static Options options() {
        Options options = new Options();
        ALL.forEach(options::addOption);
        return options;
    }

    /**
     * @param args the arguments of the application, not empty (without arguments the user interface is started)
     */
    public static CommandLineOptions parse(String... args) throws UsageException {
        // the help is shown even if other options are wrong
        if (Arrays.stream(args).anyMatch(arg -> arg.equals("-h") || arg.equals("--help"))) {
            return new CommandLineOptions(List.of(), false, false, false, false, true, false);
        }
        if (Arrays.asList(args).contains("--version")) {
            return new CommandLineOptions(List.of(), false, false, false, false, false, true);
        }
        CommandLine line;
        try {
            line = DefaultParser.builder().get().parse(options(), args);
        } catch (ParseException e) {
            throw new UsageException(e.getMessage());
        }
        if (!line.getArgList().isEmpty()) {
            throw new UsageException("unexpected argument '%s', give project-files using --run <project-file>"
                    .formatted(line.getArgList().getFirst()));
        }
        String[] projectFiles = line.getOptionValues(RUN);
        if (projectFiles == null) {
            throw new UsageException("no project-file given, use --run <project-file>");
        }
        if (line.hasOption(QUIET) && line.hasOption(VERBOSE)) {
            throw new UsageException("--quiet and --verbose cannot be combined");
        }
        return new CommandLineOptions(Arrays.stream(projectFiles).map(Path::of).toList(), line.hasOption(DRY_RUN),
                line.hasOption(QUIET), line.hasOption(VERBOSE), line.hasOption(IGNORE_INPUT_ERRORS), false, false);
    }

    public static String usage() {
        StringBuilder table = new StringBuilder();
        HelpFormatter formatter = HelpFormatter.builder().setShowSince(false)
                .setHelpAppendable(new TextHelpAppendable(table))
                // keeps the order of the definition (the sort is stable)
                .setComparator((option, other) -> 0)
                .get();
        try {
            // only the table of the options, the texts around it are formatted below
            formatter.printHelp("m3uedit --run <project-file> [options]", "", options(), "", false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String options = table.toString().lines().map(String::stripTrailing)
                .filter(line -> !line.isBlank() && !line.strip().startsWith("usage:"))
                .collect(Collectors.joining("\n"));
        return """
                Usage: m3uedit                                 start the user interface
                       m3uedit --run <project-file> [options]  run projects without user interface

                Runs projects (*.m3uproj) created with the user interface without it: their inputs are read, their
                filters, sorter and enrichers are applied and the playlist is written to their outputs. Intended
                for scripts, e.g. a cron job.

                %s

                The playlist is not written if an input could not be read, if no entries have been read or if the
                playlist is empty, so a working playlist is not replaced by an incomplete one.

                Environment:
                  M3UEDIT_SFTP_PASSWORD_<USER>_<HOST>
                      the password of the SFTP server <host> for the user <user>, e.g.
                      M3UEDIT_SFTP_PASSWORD_ROOT_192_168_178_22 for root@192.168.178.22 (upper case, other
                      characters than letters and digits replaced by _)
                  M3UEDIT_SFTP_PASSWORD
                      the password of all other SFTP servers
                  Passwords are not stored in the project. Unknown host keys are rejected, connect to a server
                  once using the user interface to accept its key.

                Exit codes:
                  0  all projects have been run and written
                  1  a project could not be run or written
                  2  wrong options, or a project-file could not be loaded
                """.formatted(options);
    }
}

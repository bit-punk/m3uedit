package org.bitpunk.m3uedit;

import org.bitpunk.m3uedit.cli.CommandLineOptions;
import org.bitpunk.m3uedit.cli.CommandLineRunner;
import org.bitpunk.m3uedit.util.AppVersion;

/**
 * Starts the user interface, or runs projects without it if command-line options are given (see
 * {@link CommandLineOptions#usage()}).
 */
public class Main {
    /**
     * The level and the target of the log (logback.xml), set before the logging is initialized.
     */
    static final String LOG_LEVEL_PROPERTY = "m3uedit.log.level";
    static final String LOG_TARGET_PROPERTY = "m3uedit.log.target";
    static final String LOG_PATTERN_PROPERTY = "m3uedit.log.pattern";
    /**
     * Errors in one line without stack trace, the run itself reports what went wrong.
     */
    static final String SHORT_LOG_PATTERN = "m3uedit: %-5level %msg%n%nopex";

    public static void main(String[] args) {
        if (args.length == 0) {
            M3UEditor.main(args);
            return;
        }
        System.exit(runCommandLine(args));
    }

    /**
     * @return the exit code
     */
    static int runCommandLine(String... args) {
        CommandLineOptions options;
        try {
            options = CommandLineOptions.parse(args);
        } catch (CommandLineOptions.UsageException e) {
            System.err.println("m3uedit: " + e.getMessage());
            System.err.println("Try 'm3uedit --help' for more information.");
            return CommandLineRunner.USAGE;
        }
        if (options.help()) {
            System.out.print(CommandLineOptions.usage());
            return CommandLineRunner.OK;
        }
        if (options.version()) {
            System.out.println("m3uedit " + AppVersion.version());
            return CommandLineRunner.OK;
        }
        // the output of the run goes to stdout, the log only to stderr
        System.setProperty(LOG_TARGET_PROPERTY, "System.err");
        System.setProperty(LOG_LEVEL_PROPERTY, options.verbose() ? "INFO" : "ERROR");
        if (!options.verbose()) {
            System.setProperty(LOG_PATTERN_PROPERTY, SHORT_LOG_PATTERN);
        }
        return new CommandLineRunner(System.out, System.err).run(options);
    }
}

package org.bitpunk.m3uedit.cli;

import org.bitpunk.m3uedit.cli.CommandLineOptions.UsageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandLineOptionsTest {

    @Test
    void testProjectsAreRunInTheOrderGiven() throws Exception {
        CommandLineOptions options = CommandLineOptions.parse("--run", "a.m3uproj", "-r", "b.m3uproj", "--run=c.m3uproj");

        assertThat(options.projects()).containsExactly(Path.of("a.m3uproj"), Path.of("b.m3uproj"), Path.of("c.m3uproj"));
        assertThat(options.dryRun()).isFalse();
        assertThat(options.quiet()).isFalse();
        assertThat(options.verbose()).isFalse();
        assertThat(options.ignoreInputErrors()).isFalse();
        assertThat(options.help()).isFalse();
    }

    @Test
    void testAllOptions() throws Exception {
        CommandLineOptions options = CommandLineOptions.parse("-n", "-q", "--ignore-input-errors", "-r", "a.m3uproj");

        assertThat(options.dryRun()).isTrue();
        assertThat(options.quiet()).isTrue();
        assertThat(options.ignoreInputErrors()).isTrue();
        assertThat(CommandLineOptions.parse("--dry-run", "--verbose", "-r", "a").verbose()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"-h", "--help"})
    void testHelpNeedsNoProject(String option) throws Exception {
        assertThat(CommandLineOptions.parse(option, "--unknown").help()).isTrue();
        // the options are listed in the order of their definition
        assertThat(CommandLineOptions.usage()).contains("M3UEDIT_SFTP_PASSWORD_<USER>_<HOST>", "M3UEDIT_SFTP_PASSWORD\n", "Exit codes")
                .containsSubsequence("-r, --run <project-file>", "-n, --dry-run", "--ignore-input-errors", "-q, --quiet",
                        "-v, --verbose", "-h, --help");
    }

    @Test
    void testWrongCalls() {
        assertThatThrownBy(() -> CommandLineOptions.parse("--foo")).isInstanceOf(UsageException.class)
                .hasMessageContaining("Unrecognized option: --foo");
        assertThatThrownBy(() -> CommandLineOptions.parse("a.m3uproj")).isInstanceOf(UsageException.class)
                .hasMessageContaining("unexpected argument 'a.m3uproj'");
        assertThatThrownBy(() -> CommandLineOptions.parse("--run")).isInstanceOf(UsageException.class)
                .hasMessageContaining("Missing argument for option: r");
        assertThatThrownBy(() -> CommandLineOptions.parse("--dry-run")).isInstanceOf(UsageException.class)
                .hasMessageContaining("no project-file given");
        assertThatThrownBy(() -> CommandLineOptions.parse("-q", "-v", "-r", "a")).isInstanceOf(UsageException.class)
                .hasMessage("--quiet and --verbose cannot be combined");
    }
}

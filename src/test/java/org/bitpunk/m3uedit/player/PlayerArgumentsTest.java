package org.bitpunk.m3uedit.player;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerArgumentsTest {

    @Test
    void testArgumentsAreSeparatedBySpaces() {
        assertThat(PlayerArguments.split("  --fullscreen   --no-osd ")).containsExactly("--fullscreen", "--no-osd");
        assertThat(PlayerArguments.split("")).isEmpty();
        assertThat(PlayerArguments.split(null)).isEmpty();
    }

    @Test
    void testArgumentsContainingSpacesAreQuoted() {
        List<String> arguments = List.of("--fullscreen", "--video-title=My TV", "");

        String line = PlayerArguments.join(arguments);

        assertThat(line).isEqualTo("--fullscreen \"--video-title=My TV\" \"\"");
        assertThat(PlayerArguments.split(line)).isEqualTo(arguments);
    }

    @Test
    void testQuotesWithinAnArgument() {
        assertThat(PlayerArguments.split("--title=\"My TV\" -f")).containsExactly("--title=My TV", "-f");
    }
}

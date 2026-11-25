package org.bitpunk.m3uedit.player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The arguments of the external player as a line to edit: separated by spaces, an argument containing spaces is put
 * in double quotes, e.g. {@code --fullscreen "--video-title=My TV"}.
 */
public final class PlayerArguments {

    private PlayerArguments() {
        // Utility class
    }

    public static List<String> split(String line) {
        List<String> arguments = new ArrayList<>();
        if (line == null) {
            return arguments;
        }
        StringBuilder argument = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                started = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (started) {
                    arguments.add(argument.toString());
                    argument.setLength(0);
                    started = false;
                }
            } else {
                argument.append(c);
                started = true;
            }
        }
        if (started) {
            arguments.add(argument.toString());
        }
        return arguments;
    }

    public static String join(List<String> arguments) {
        if (arguments == null) {
            return "";
        }
        return arguments.stream()
                .map(argument -> argument.isEmpty() || argument.chars().anyMatch(Character::isWhitespace)
                        ? "\"" + argument + "\"" : argument)
                .collect(Collectors.joining(" "));
    }
}

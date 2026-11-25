package org.bitpunk.m3uedit.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plugin API is what plugins are written against: it must not depend on the rest of m3uedit, and only on Java SE,
 * JavaFX and Lombok (which is used at compile time only).
 */
class ApiBoundaryTest {
    private static final Path API = Path.of("src/main/java/org/bitpunk/m3uedit/api");
    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?([\\w.]+)", Pattern.MULTILINE);
    private static final List<String> ALLOWED = List.of("java.", "javafx.", "lombok.", "org.bitpunk.m3uedit.api.");

    @Test
    void testTheApiOnlyUsesJavaAndJavaFx() throws IOException {
        List<String> forbidden = new ArrayList<>();
        try (Stream<Path> files = Files.walk(API)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher matcher = IMPORT.matcher(source);
                while (matcher.find()) {
                    String imported = matcher.group(1);
                    if (ALLOWED.stream().noneMatch(imported::startsWith)) {
                        forbidden.add(file.getFileName() + ": " + imported);
                    }
                }
                // fully qualified references in the code or the Javadoc
                Matcher qualified = Pattern.compile("org\\.bitpunk\\.m3uedit\\.(?!api\\b)[\\w.]+").matcher(source);
                while (qualified.find()) {
                    forbidden.add(file.getFileName() + ": " + qualified.group());
                }
            }
        }

        assertThat(forbidden).isEmpty();
    }

    @Test
    void testTheApiHasNoAnnotationsOfJacksonOrValidation() throws IOException {
        try (Stream<Path> files = Files.walk(API)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java")))
                    .allSatisfy(file -> assertThat(Files.readString(file))
                            .doesNotContain("com.fasterxml", "tools.jackson", "jakarta.validation"));
        }
    }
}

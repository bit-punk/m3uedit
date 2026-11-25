package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The texts of the built-in plugins ({@code org/bitpunk/m3uedit/plugins/messages*.properties}): the same keys in both
 * languages, apostrophes matching the formatting, and every key used by the plugins exists.
 */
class PluginMessagesTest {
    private static final String BUNDLE = "org/bitpunk/m3uedit/plugins/";
    // the plugins are a module of their own; their tests are part of m3uedit-app (they use its context)
    private static final Path SOURCES = Path.of("../m3uedit-plugins/src/main/java/org/bitpunk/m3uedit/plugins");
    private static final Path RESOURCES = Path.of("../m3uedit-plugins/src/main/resources/plugin");
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)[^}]*}");
    private static final Pattern FXML_KEY = Pattern.compile("=\"%([^\"]+)\"");
    private static final Pattern JAVA_KEY = Pattern.compile("(?:\\btext\\(\\s*|nameKey\\s*=\\s*)\"([^\"]+)\"");

    private static Properties bundle(String fileName) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = PluginMessagesTest.class.getClassLoader().getResourceAsStream(BUNDLE + fileName)) {
            assertThat(in).as(BUNDLE + fileName).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static Set<String> arguments(String text) {
        Set<String> arguments = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(text);
        while (matcher.find()) {
            arguments.add(matcher.group(1));
        }
        return arguments;
    }

    private static Set<String> found(Path directory, String suffix, Pattern pattern) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(suffix))
                    // the plugin services in the package itself are part of m3uedit
                    .filter(path -> !path.getParent().equals(SOURCES)).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }

    @Test
    void testBothLanguagesHaveTheSameTexts() throws Exception {
        Properties english = bundle("messages.properties");
        Properties german = bundle("messages_de.properties");

        assertThat(german.stringPropertyNames()).as("keys missing or superfluous in German")
                .containsExactlyInAnyOrderElementsOf(english.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            assertThat(arguments(german.getProperty(key))).as("arguments of " + key)
                    .isEqualTo(arguments(english.getProperty(key)));
            assertThat(german.getProperty(key)).as("German text of " + key).isNotBlank();
        }
    }

    @Test
    void testApostrophesMatchTheFormatting() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (String fileName : List.of("messages.properties", "messages_de.properties")) {
            Properties texts = bundle(fileName);
            for (String key : texts.stringPropertyNames()) {
                String text = texts.getProperty(key);
                boolean formatted = !arguments(text).isEmpty();
                if (formatted ? text.replace("''", "").contains("'") : text.contains("''")) {
                    wrong.add(fileName + ": " + key + "=" + text);
                }
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    void testEveryKeyUsedExists() throws Exception {
        Set<String> keys = bundle("messages.properties").stringPropertyNames();
        Set<String> used = new TreeSet<>(found(RESOURCES, ".fxml", FXML_KEY));
        used.addAll(found(SOURCES, ".java", JAVA_KEY));

        assertThat(used).as("keys used by the plugins").isNotEmpty();
        assertThat(used.stream().filter(key -> !keys.contains(key)).toList())
                .as("keys used but missing in " + BUNDLE + "messages.properties").isEmpty();
    }
}

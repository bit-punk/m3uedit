package org.bitpunk.m3uedit.i18n;

import org.bitpunk.m3uedit.config.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesTest {
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)[^}]*}");
    private static final Pattern FXML_KEY = Pattern.compile("=\"%([^\"]+)\"");
    private static final Pattern JAVA_KEY = Pattern.compile("Messages\\.get\\(\\s*\"([^\"]+)\"");

    @AfterEach
    void useEnglish() {
        Messages.use(Locale.ENGLISH);
    }

    private static Properties bundle(String fileName) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getClassLoader().getResourceAsStream("i18n/" + fileName)) {
            // read as UTF-8, like ResourceBundle does
            properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static Set<String> found(Path directory, String suffix, Pattern pattern) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(suffix)).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }

    private static Set<String> arguments(String text) {
        Set<String> arguments = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(text);
        while (matcher.find()) {
            arguments.add(matcher.group(1));
        }
        return arguments;
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
                // formatted: each apostrophe doubled; not formatted: used as it is
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
        Set<String> used = new TreeSet<>(found(Path.of("src/main/resources"), ".fxml", FXML_KEY));
        used.addAll(found(Path.of("src/main/java"), ".java", JAVA_KEY));

        assertThat(used.stream().filter(key -> !keys.contains(key)).collect(Collectors.toList()))
                .as("keys used but missing in messages.properties").isEmpty();
    }

    @Test
    void testTheLanguageOfTheSystemIsUsedIfItIsSupported() {
        assertThat(Language.SYSTEM.locale(Locale.GERMANY)).isEqualTo(Locale.GERMANY);
        assertThat(Language.SYSTEM.locale(Locale.FRANCE)).isEqualTo(Locale.ENGLISH);
        assertThat(Language.ENGLISH.locale(Locale.GERMANY)).isEqualTo(Locale.ENGLISH);
        assertThat(Language.GERMAN.locale(Locale.US)).isEqualTo(Locale.GERMAN);
    }

    @Test
    void testTheTextsOfTheLanguageAreUsed() {
        assertThat(Messages.get("common.cancel")).isEqualTo("Cancel");

        Messages.use(Locale.GERMANY);
        assertThat(Messages.get("common.cancel")).isEqualTo("Abbrechen");
        assertThat(Messages.locale().getLanguage()).isEqualTo("de");

        // no fallback to the language of the system
        Messages.use(Locale.FRANCE);
        assertThat(Messages.get("common.cancel")).isEqualTo("Cancel");
        assertThat(Messages.locale()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void testAMissingKeyIsShownAsItIs() {
        assertThat(Messages.get("no.such.key")).isEqualTo("no.such.key");
    }
}

package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginApi;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.testsupport.ExamplePluginJar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plugins of other parties installed as jars, here the jar of the example plugin (module m3uedit-example-plugin).
 */
class PluginJarsTest {
    private static final String SERVICES = "META-INF/services/org.bitpunk.m3uedit.api.Filter";

    @TempDir
    Path extensions;

    @AfterEach
    void english() {
        Messages.use(Locale.ENGLISH);
    }

    private PluginJars load() {
        return PluginJars.load(extensions, PluginJarsTest.class.getClassLoader());
    }

    private static Entry entry(String name, String radio) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setRadio(radio);
        return entry;
    }

    @Test
    void testTheExamplePluginIsNotPartOfTheClasspath() {
        assertThat(PluginJarsTest.class.getClassLoader().getResource(SERVICES.replace("Filter", "Filter")))
                .as("only the built-in filters are on the classpath").satisfiesAnyOf(
                        url -> assertThat(url).isNull(),
                        url -> assertThat(url.toString()).doesNotContain("example"));
        assertThat(FilterPluginService.getInstance().getDescriptors()).extracting(PluginDescriptor::id)
                .doesNotContain(ExamplePluginJar.RADIO_FILTER);
    }

    @Test
    void testTheExamplePluginDeclaresTheVersionOfTheApi() throws Exception {
        try (InputStream in = Files.newInputStream(ExamplePluginJar.classes().resolve("META-INF/MANIFEST.MF"))) {
            assertThat(new Manifest(in).getMainAttributes().getValue(PluginApi.MANIFEST_ATTRIBUTE))
                    .isEqualTo(String.valueOf(PluginApi.VERSION));
        }
    }

    @Test
    void testAPluginOfAJarIsOfferedAfterTheBuiltInOnes() throws Exception {
        Path jar = ExamplePluginJar.build(extensions, "radio-filter.jar");

        PluginJars jars = load();
        PluginService<Filter> filters = AbstractPluginService.of(Filter.class, jars);

        assertThat(jars.getJars()).extracting(PluginJars.Jar::file).containsExactly(jar);
        assertThat(jars.getProblems()).isEmpty();
        List<PluginDescriptor<Filter>> descriptors = filters.getDescriptors();
        assertThat(descriptors).filteredOn(PluginDescriptor::isInstalled).singleElement().satisfies(radio -> {
            assertThat(radio.id()).isEqualTo(ExamplePluginJar.RADIO_FILTER);
            assertThat(radio.jar()).isEqualTo(jar);
            assertThat(radio.name()).isEqualTo("Radio Filter (example)");
        });
        // the built-in filters once, although the class loader of the jar finds them as well
        assertThat(descriptors).hasSize(FilterPluginService.getInstance().getDescriptors().size() + 1);
        assertThat(descriptors.getLast().id()).isEqualTo(ExamplePluginJar.RADIO_FILTER);
    }

    @Test
    void testThePluginOfAJarWorksWithItsOwnTexts() throws Exception {
        ExamplePluginJar.build(extensions, "radio-filter.jar");
        PluginDescriptor<Filter> radio = AbstractPluginService.of(Filter.class, load()).find(ExamplePluginJar.RADIO_FILTER)
                .orElseThrow();
        Entry tv = entry("Das Erste", null);
        Entry radioChannel = entry("WDR 2", "true");

        Filter filter = radio.create();

        assertThat(filter.getClass().getClassLoader()).isNotSameAs(PluginJarsTest.class.getClassLoader());
        assertThat(filter.filter(List.of(tv, radioChannel))).containsExactly(tv);
        assertThat(filter.describeRemoval(radioChannel, List.of(tv, radioChannel))).isEqualTo("radio channel");
        Messages.use(Locale.GERMAN);
        assertThat(radio.name()).isEqualTo("Radio-Filter (Beispiel)");
        assertThat(radio.create().getDescription()).startsWith("Entfernt die Radiosender");
    }

    @Test
    void testOtherFilesAndNoDirectoryAreIgnored() throws Exception {
        Files.writeString(extensions.resolve("README.txt"), "not a plugin");

        assertThat(load().getJars()).isEmpty();
        assertThat(load().getProblems()).isEmpty();
        assertThat(PluginJars.load(extensions.resolve("missing"), getClass().getClassLoader()).getJars()).isEmpty();
    }

    @Test
    void testAJarThatCannotBeLoadedIsSkippedWithTheReason() throws Exception {
        Files.writeString(extensions.resolve("a-broken.jar"), "not a jar");
        ExamplePluginJar.build(extensions, "b-no-version.jar", manifest -> {
            manifest.getMainAttributes().remove(new Attributes.Name(PluginApi.MANIFEST_ATTRIBUTE));
            return manifest;
        }, Map.of());
        ExamplePluginJar.build(extensions, "c-other-version.jar", manifest -> {
            manifest.getMainAttributes().putValue(PluginApi.MANIFEST_ATTRIBUTE, "99");
            return manifest;
        }, Map.of());
        ExamplePluginJar.build(extensions, "d-no-plugin.jar", manifest -> manifest, Map.of(SERVICES, ""));
        Path good = ExamplePluginJar.build(extensions, "e-good.jar");

        PluginJars jars = load();

        assertThat(jars.getJars()).extracting(PluginJars.Jar::file).containsExactly(good);
        assertThat(jars.getProblems()).extracting(problem -> problem.file().getFileName().toString(),
                        PluginJars.Problem::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a-broken.jar", jars.getProblems().get(0).reason()),
                        org.assertj.core.groups.Tuple.tuple("b-no-version.jar",
                                "it does not declare the version of the plugin API it was built for "
                                        + "(M3UEdit-Plugin-API in its manifest)"),
                        org.assertj.core.groups.Tuple.tuple("c-other-version.jar",
                                "built for version 99 of the plugin API, m3uedit supports version 1"),
                        org.assertj.core.groups.Tuple.tuple("d-no-plugin.jar",
                                "it contains no plugin (no META-INF/services/org.bitpunk.m3uedit.api.*)"));
        assertThat(jars.getProblems().getFirst().reason()).startsWith("not a jar");
    }

    @Test
    void testAPluginThatCannotBeLoadedIsSkippedWithTheReason() throws Exception {
        Path jar = ExamplePluginJar.build(extensions, "missing-class.jar", manifest -> manifest,
                Map.of(SERVICES, "de.example.Missing\n" + ExamplePluginJar.RADIO_FILTER + "\n"));

        PluginJars jars = load();
        PluginService<Filter> filters = AbstractPluginService.of(Filter.class, jars);

        // the other plugins of the jar are loaded
        assertThat(filters.find(ExamplePluginJar.RADIO_FILTER)).isPresent();
        assertThat(jars.getProblems()).singleElement().satisfies(problem -> {
            assertThat(problem.file()).isEqualTo(jar);
            assertThat(problem.reason()).startsWith("a plugin cannot be loaded").contains("de.example.Missing");
        });
    }

    @Test
    void testAPluginWithTheIdOfAnotherOneIsSkipped() throws Exception {
        Path first = ExamplePluginJar.build(extensions, "a-radio.jar");
        Path second = ExamplePluginJar.build(extensions, "b-radio-copy.jar");

        PluginJars jars = load();
        PluginService<Filter> filters = AbstractPluginService.of(Filter.class, jars);

        assertThat(filters.find(ExamplePluginJar.RADIO_FILTER)).hasValueSatisfying(radio ->
                assertThat(radio.jar()).isEqualTo(first));
        assertThat(jars.getProblems()).singleElement().satisfies(problem -> {
            assertThat(problem.file()).isEqualTo(second);
            assertThat(problem.reason()).contains(ExamplePluginJar.RADIO_FILTER, "already used");
        });
    }
}

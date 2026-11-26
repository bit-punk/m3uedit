package org.bitpunk.m3uedit.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Builds the jar of the example plugin (module {@code m3uedit-example-plugin}) from its classes, like the jar a user
 * installs; it is not on the classpath of the tests (see the configuration of surefire), so it is only found as jar.
 */
public final class ExamplePluginJar {
    public static final String RADIO_FILTER = "org.bitpunk.m3uedit.example.RadioFilter";

    private ExamplePluginJar() {
        // Utility class
    }

    /**
     * @return the classes and resources of the example plugin
     */
    public static Path classes() {
        String classes = System.getProperty("m3uedit.examplePlugin.classes",
                "../m3uedit-example-plugin/target/classes");
        return Path.of(classes);
    }

    /**
     * @return the jar of the example plugin, as built by its module
     */
    public static Path build(Path directory, String fileName) throws IOException {
        return build(directory, fileName, UnaryOperator.identity(), Map.of());
    }

    /**
     * @param manifest changes the manifest, e.g. its version of the plugin API
     * @param replaced files replacing those of the plugin (path in the jar → content), e.g. its services; an empty
     *                 content removes the file
     */
    public static Path build(Path directory, String fileName, UnaryOperator<Manifest> manifest,
                             Map<String, String> replaced) throws IOException {
        Path classes = classes();
        Manifest original;
        try (InputStream in = Files.newInputStream(classes.resolve("META-INF/MANIFEST.MF"))) {
            original = new Manifest(in);
        }
        // without it, a manifest is written without its main attributes
        original.getMainAttributes().putIfAbsent(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        Files.createDirectories(directory);
        Path jar = directory.resolve(fileName);
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jarOut = new JarOutputStream(out, manifest.apply(original));
             Stream<Path> files = Files.walk(classes)) {
            List<Path> entries = files.filter(Files::isRegularFile).sorted().toList();
            for (Path file : entries) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                if (name.equals("META-INF/MANIFEST.MF") || replaced.containsKey(name)) {
                    continue;
                }
                jarOut.putNextEntry(new JarEntry(name));
                Files.copy(file, jarOut);
                jarOut.closeEntry();
            }
            for (Map.Entry<String, String> file : replaced.entrySet()) {
                if (!file.getValue().isEmpty()) {
                    jarOut.putNextEntry(new JarEntry(file.getKey()));
                    jarOut.write(file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    jarOut.closeEntry();
                }
            }
        }
        return jar;
    }
}

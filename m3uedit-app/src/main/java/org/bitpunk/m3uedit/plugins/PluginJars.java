package org.bitpunk.m3uedit.plugins;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.PluginApi;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.i18n.Messages;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * The jars of plugins of other parties, installed by the user in {@code ~/.m3uedit/extensions/}; read once at startup.
 * <p>
 * Each jar gets a class loader of its own with m3uedit as parent, which is asked first: the plugin API, JavaFX and the
 * libraries of m3uedit are the classes of m3uedit, a jar must not contain other versions of them. A jar is only loaded
 * if it declares the version of the plugin API it was built for ({@link PluginApi#MANIFEST_ATTRIBUTE}) and that is
 * the version of m3uedit. Jars that cannot be loaded, and plugins in them that cannot be used, are listed with the
 * reason ({@link #getProblems()}).
 */
@Slf4j
public final class PluginJars {
    public static final String DIRECTORY_NAME = "extensions";
    private static final String SERVICES = "META-INF/services/org.bitpunk.m3uedit.api.";

    /**
     * A jar loaded.
     */
    public record Jar(Path file, ClassLoader classLoader) {
    }

    /**
     * A jar, or a plugin in it, that could not be loaded.
     *
     * @param reason why, in the language of the user interface
     */
    public record Problem(Path file, String reason) {
    }

    private final Path directory;
    private final List<Jar> jars;
    private final List<Problem> problems = Collections.synchronizedList(new ArrayList<>());

    private PluginJars(Path directory, List<Jar> jars, List<Problem> problems) {
        this.directory = directory;
        this.jars = List.copyOf(jars);
        this.problems.addAll(problems);
    }

    /**
     * @return the jars installed by the user, read when first needed
     */
    public static PluginJars installed() {
        return Holder.INSTALLED;
    }

    private static final class Holder {
        private static final PluginJars INSTALLED = load(directory(), PluginJars.class.getClassLoader());
    }

    /**
     * @return the directory of the plugin jars, {@code ~/.m3uedit/extensions}
     */
    public static Path directory() {
        return AppConfigPaths.getBaseDir().resolve(DIRECTORY_NAME);
    }

    /**
     * @return no jars, e.g. for tests of the built-in plugins
     */
    public static PluginJars none() {
        return new PluginJars(null, List.of(), List.of());
    }

    /**
     * Loads the jars of the given directory, in the order of their names.
     *
     * @param parent the class loader of m3uedit, the parent of the class loaders of the jars
     */
    public static PluginJars load(Path directory, ClassLoader parent) {
        List<Jar> jars = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        if (directory != null && Files.isDirectory(directory)) {
            List<Path> files;
            try (Stream<Path> paths = Files.list(directory)) {
                files = paths.filter(path -> path.getFileName().toString().endsWith(".jar")).sorted().toList();
            } catch (IOException e) {
                log.error("the plugin jars in {} cannot be read", directory, e);
                files = List.of();
            }
            for (Path file : files) {
                String problem = check(file);
                if (problem != null) {
                    log.warn("plugin jar {} is not loaded: {}", file, problem);
                    problems.add(new Problem(file, problem));
                } else {
                    try {
                        URL url = file.toUri().toURL();
                        jars.add(new Jar(file, new URLClassLoader("plugin " + file.getFileName(), new URL[]{url}, parent)));
                        log.info("plugin jar {} loaded", file);
                    } catch (MalformedURLException e) {
                        problems.add(new Problem(file, e.getMessage()));
                    }
                }
            }
        }
        return new PluginJars(directory, jars, problems);
    }

    /**
     * @return why the jar cannot be loaded, null if it can
     */
    static String check(Path file) {
        try (JarFile jar = new JarFile(file.toFile())) {
            Manifest manifest = jar.getManifest();
            String version = manifest == null ? null : manifest.getMainAttributes().getValue(PluginApi.MANIFEST_ATTRIBUTE);
            if (version == null || version.isBlank()) {
                return Messages.get("pluginJars.problem.noApiVersion", PluginApi.MANIFEST_ATTRIBUTE);
            }
            if (!version.strip().equals(String.valueOf(PluginApi.VERSION))) {
                return Messages.get("pluginJars.problem.otherApiVersion", version.strip(), PluginApi.VERSION);
            }
            if (jar.stream().noneMatch(entry -> entry.getName().startsWith(SERVICES))) {
                return Messages.get("pluginJars.problem.noPlugin");
            }
            return null;
        } catch (IOException | SecurityException e) {
            return Messages.get("pluginJars.problem.notAJar", e.getMessage());
        }
    }

    /**
     * @return the directory read, null if none
     */
    public Path getDirectory() {
        return directory;
    }

    public List<Jar> getJars() {
        return jars;
    }

    /**
     * @return the jars, and the plugins in them, that could not be loaded; complete once all plugin services have been
     * created (see {@link PluginServices#all()})
     */
    public List<Problem> getProblems() {
        synchronized (problems) {
            return List.copyOf(problems);
        }
    }

    /**
     * Reports a plugin of a jar that cannot be used.
     */
    void report(Path file, String reason) {
        log.warn("plugin of {} is not loaded: {}", file, reason);
        problems.add(new Problem(file, reason));
    }
}

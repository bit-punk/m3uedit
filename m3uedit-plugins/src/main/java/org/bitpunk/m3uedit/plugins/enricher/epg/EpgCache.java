package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.api.PluginContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The directory the EPG sources are downloaded to ({@code ~/.m3uedit/cache/epg}): how much space it takes and
 * clearing it. A cleared source is downloaded again when it is needed next.
 */
public final class EpgCache {

    /**
     * The name of the directory within the cache directory of m3uedit.
     */
    static final String DIRECTORY_NAME = "epg";

    private EpgCache() {
        // Utility class
    }

    /**
     * The space the downloaded sources take.
     *
     * @param bytes the size of all files
     * @param files the number of files
     */
    public record Usage(long bytes, int files) {
        public static final Usage EMPTY = new Usage(0, 0);

        /**
         * @return e.g. "125.3 MB in 2 files", or "empty"
         */
        public String describe(PluginContext context) {
            return context.text("epgEnricher.cache.usage", files == 0 ? "" : formatSize(context, bytes), files);
        }
    }

    public static Path directory(PluginContext context) {
        return context.cacheDirectory().resolve(DIRECTORY_NAME);
    }

    /**
     * @return the downloaded sources as data collected by the EPG Enricher, which the user can clear in the
     * configuration of m3uedit
     */
    public static CollectedData collectedData(PluginContext context) {
        Path directory = directory(context);
        return new CollectedData() {
            @Override
            public String getId() {
                return "org.bitpunk.m3uedit.epg.cache";
            }

            @Override
            public String getName() {
                return context.text("epgEnricher.cache.name");
            }

            @Override
            public String describe() {
                return usage(directory).describe(context);
            }

            @Override
            public String getHint() {
                return context.text("epgEnricher.cache.hint");
            }

            @Override
            public boolean isEmpty() {
                return usage(directory).files() == 0;
            }

            @Override
            public void clear() throws IOException {
                EpgCache.clear(directory);
            }
        };
    }

    public static Usage usage(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Usage.EMPTY;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            long bytes = 0;
            for (Path file : files) {
                bytes += Files.size(file);
            }
            return new Usage(bytes, files.size());
        } catch (IOException e) {
            return Usage.EMPTY;
        }
    }

    /**
     * Deletes all downloaded sources.
     *
     * @throws IOException if a file could not be deleted, e.g. because it is being downloaded
     */
    public static void clear(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> contents;
        try (Stream<Path> paths = Files.walk(directory)) {
            // the directory itself stays
            contents = paths.filter(path -> !path.equals(directory)).sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : contents) {
            Files.deleteIfExists(path);
        }
    }

    static String formatSize(PluginContext context, long bytes) {
        if (bytes < 1024) {
            return context.text("epgEnricher.cache.bytes", bytes);
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = -1;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        // with the decimal separator of the language of the user interface
        return String.format(context.locale(), "%.1f %s", size, units[unit]);
    }
}

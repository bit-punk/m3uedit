package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.i18n.Messages;

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
        public String describe() {
            return Messages.get("configuration.epgCache.usage", files == 0 ? "" : formatSize(bytes), files);
        }
    }

    /**
     * The name of the directory within the cache directory of m3uedit.
     */
    static final String DIRECTORY_NAME = "epg";

    public static Path directory() {
        return AppConfigPaths.getBaseDir().resolve("cache").resolve(DIRECTORY_NAME);
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

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return Messages.get("configuration.epgCache.bytes", bytes);
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = -1;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        // with the decimal separator of the language of the user interface
        return String.format(Messages.locale(), "%.1f %s", size, units[unit]);
    }
}

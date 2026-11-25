package org.bitpunk.m3uedit.api;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Everything an {@link Output} writes: the playlist and the additional files provided by the enrichers.
 *
 * @param playlist the entries of the playlist
 * @param epgUrls  the EPG urls written to the playlist header, unless an additional file replaces them
 * @param files    the additional files to write next to the playlist
 */
public record Export(List<Entry> playlist, List<String> epgUrls, List<ExportFile> files) {

    /**
     * An additional file, created once for all outputs.
     *
     * @param name             a short description shown to the user, e.g. in errors
     * @param suffix           appended to the name of the playlist without its extension
     * @param headerAttributes the attributes of the playlist header referencing the file by its url
     * @param content          the (temporary) file containing the content
     */
    public record ExportFile(String name, String suffix, List<String> headerAttributes, Path content) {
        public ExportFile {
            headerAttributes = headerAttributes == null ? List.of() : List.copyOf(headerAttributes);
        }

        /**
         * @param playlistFileName the file name of the playlist, e.g. {@code tv.m3u}
         * @return the file name of this file next to the playlist, e.g. {@code tv.xml.gz}
         */
        public String fileNameFor(String playlistFileName) {
            String name = playlistFileName == null || playlistFileName.isBlank() ? "playlist" : playlistFileName;
            int dot = name.lastIndexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;
            return baseName + suffix;
        }
    }

    public Export {
        playlist = playlist == null ? List.of() : List.copyOf(playlist);
        epgUrls = epgUrls == null ? List.of() : List.copyOf(epgUrls);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static Export of(List<Entry> playlist) {
        return new Export(playlist, List.of(), List.of());
    }

    /**
     * Returns the attributes of the playlist header: the EPG urls, replaced by the urls of the additional files
     * referenced by the same attributes. Files without url (e.g. no url known for players) are not referenced.
     *
     * @param urlOf returns the url players read an additional file from, null if there is none
     * @return the attribute names and their (comma-separated) values, in the order they are written
     */
    public Map<String, String> headerAttributes(Function<ExportFile, String> urlOf) {
        Map<String, Set<String>> fileUrls = new LinkedHashMap<>();
        for (ExportFile file : files) {
            String url = urlOf.apply(file);
            if (url != null && !url.isBlank()) {
                file.headerAttributes().forEach(attribute ->
                        fileUrls.computeIfAbsent(attribute, key -> new LinkedHashSet<>()).add(url.strip()));
            }
        }
        Map<String, String> attributes = new LinkedHashMap<>(epgAttributes(epgUrls));
        fileUrls.forEach((attribute, urls) -> attributes.put(attribute, String.join(",", urls)));
        return attributes;
    }

    /**
     * The EPG urls are referenced by {@code x-tvg-url} and {@code url-tvg}, as players support either of them.
     *
     * @return the header attributes referencing the given EPG urls (comma-separated), none if there are no urls
     */
    public static Map<String, String> epgAttributes(List<String> epgUrls) {
        String urls = epgUrls == null ? "" : epgUrls.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        Map<String, String> attributes = new LinkedHashMap<>();
        if (!urls.isEmpty()) {
            attributes.put("x-tvg-url", urls);
            attributes.put("url-tvg", urls);
        }
        return attributes;
    }
}

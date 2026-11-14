package org.bitpunk.m3uedit.plugins.enricher;

import org.bitpunk.m3uedit.model.Entry;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * A file provided by an {@link Enricher}, written by each output next to the playlist, e.g. a programme guide.
 * <p>
 * The file is named after the playlist: {@code tv.m3u} and the suffix {@code .xml.gz} give {@code tv.xml.gz}.
 * Its content is only created when the playlist is written, once for all outputs.
 *
 * @param name             a short description shown to the user, e.g. in errors ("EPG guide")
 * @param suffix           appended to the name of the playlist without its extension, e.g. {@code .xml.gz}
 * @param headerAttributes the attributes of the playlist header referencing this file by its url,
 *                         e.g. {@code x-tvg-url}; none if empty
 * @param content          writes the content of the file
 */
public record Attachment(String name, String suffix, List<String> headerAttributes, Content content) {

    /**
     * Writes the content of an attachment.
     */
    @FunctionalInterface
    public interface Content {
        /**
         * Runs in the background, so it may block (e.g. read from the network).
         *
         * @param playlist the playlist written, with the manual changes of the user applied
         */
        void write(List<Entry> playlist, OutputStream outputStream) throws IOException;
    }

    public Attachment {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        if (suffix == null || suffix.isBlank() || suffix.contains("/") || suffix.contains("\\")) {
            throw new IllegalArgumentException("Invalid suffix of attachment '%s': %s".formatted(name, suffix));
        }
        headerAttributes = headerAttributes == null ? List.of() : List.copyOf(headerAttributes);
    }
}

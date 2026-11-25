package org.bitpunk.m3uedit.plugins.output.file;

/**
 * The settings of the {@link FileOutput} stored in project-files.
 *
 * @param output the URI of the file written, e.g. {@code file:///tmp/playlist.m3u}; null while not configured
 */
public record FileOutputSettings(String output) {
}

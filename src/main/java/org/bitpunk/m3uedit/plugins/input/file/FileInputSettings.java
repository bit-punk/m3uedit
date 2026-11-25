package org.bitpunk.m3uedit.plugins.input.file;

/**
 * The settings of a {@link FileInput} stored in the project-file.
 *
 * @param source the absolute path of the playlist file, null if none has been chosen yet
 */
public record FileInputSettings(String source) {
}

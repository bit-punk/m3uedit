package org.bitpunk.m3uedit.api;

/**
 * The settings of a plugin without settings.
 */
public record NoSettings() {
    public static final NoSettings INSTANCE = new NoSettings();
}

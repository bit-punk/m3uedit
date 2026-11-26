package org.bitpunk.m3uedit.example;

/**
 * The settings of the {@link RadioFilter}, stored in the project-file.
 *
 * @param keepRadio true to remove the TV channels and keep the radio channels, false to remove the radio channels
 */
public record RadioFilterSettings(Boolean keepRadio) {
}

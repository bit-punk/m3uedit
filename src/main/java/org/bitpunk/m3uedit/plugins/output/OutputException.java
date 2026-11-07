package org.bitpunk.m3uedit.plugins.output;

/**
 * Thrown if a playlist cannot be written to an {@link Output}.
 * The message is meant to be shown to the user.
 */
public class OutputException extends RuntimeException {
    public OutputException(String message, Throwable cause) {
        super(message, cause);
    }
}

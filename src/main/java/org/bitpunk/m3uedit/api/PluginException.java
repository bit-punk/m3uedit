package org.bitpunk.m3uedit.api;

/**
 * An error of a plugin to be shown to the user, e.g. an input that could not be read or an output that could not be
 * written. Its message is shown as it is, so it has to be understandable for the user and in the language of the user
 * interface (see {@link PluginContext#text(String, Object...)}).
 */
public class PluginException extends RuntimeException {

    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
}

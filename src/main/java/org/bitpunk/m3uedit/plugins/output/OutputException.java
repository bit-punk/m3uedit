package org.bitpunk.m3uedit.plugins.output;

import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.PluginException;

/**
 * Thrown if a playlist cannot be written to an {@link Output}.
 * The message is meant to be shown to the user.
 */
public class OutputException extends PluginException {
    public OutputException(String message, Throwable cause) {
        super(message, cause);
    }
}

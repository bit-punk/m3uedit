package org.bitpunk.m3uedit.plugins.missing;

import org.bitpunk.m3uedit.api.Export;
import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.PluginException;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;

/**
 * An output of a project-file that is not installed: the playlist is not written to it, which is reported.
 */
public class MissingOutput extends MissingPlugin implements Output {

    public MissingOutput(String typeId, ObjectNode settings) {
        super(typeId, settings);
    }

    @Override
    public void write(Export export) {
        throw new PluginException(notInstalled());
    }

    @Override
    public URI getOutput() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }
}

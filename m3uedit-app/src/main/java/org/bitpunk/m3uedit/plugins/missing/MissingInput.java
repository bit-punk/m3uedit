package org.bitpunk.m3uedit.plugins.missing;

import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Playlist;
import org.bitpunk.m3uedit.api.PluginException;
import tools.jackson.databind.node.ObjectNode;

/**
 * An input of a project-file that is not installed: it reads no entries and is reported as input that could not be
 * read.
 */
public class MissingInput extends MissingPlugin implements Input {

    public MissingInput(String typeId, ObjectNode settings) {
        super(typeId, settings);
    }

    @Override
    public Playlist read() {
        throw new PluginException(notInstalled());
    }

    @Override
    public String getSource() {
        return getTypeId();
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }
}

package org.bitpunk.m3uedit.plugins.missing;

import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.plugins.PluginJars;
import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * A plugin of a project-file that is not installed (e.g. the jar of a plugin of another party has been removed): only
 * its data is kept, no class is loaded for it. It is written back to the project-file unchanged, so the project uses
 * the plugin with these settings again once it is installed. It does not take part in the computation of the
 * playlist.
 */
public abstract class MissingPlugin implements Plugin {
    private final String typeId;
    private final ObjectNode settings;

    /**
     * @param typeId   the type id of the plugin, as in the project-file
     * @param settings its settings, as in the project-file (without its type and whether it is enabled)
     */
    protected MissingPlugin(String typeId, ObjectNode settings) {
        this.typeId = Objects.requireNonNull(typeId);
        this.settings = settings.deepCopy();
    }

    public String getTypeId() {
        return typeId;
    }

    /**
     * @return a copy of the settings as in the project-file
     */
    public ObjectNode getRawSettings() {
        return settings.deepCopy();
    }

    /**
     * @return the type id: the name of a plugin that is not installed is not known (it is marked as not installed by
     * the pipeline)
     */
    @Override
    public String getName() {
        return typeId;
    }

    @Override
    public String getDescription() {
        return Messages.get("missingPlugin.description", typeId, PluginJars.directory());
    }

    /**
     * @return the error reported when the plugin would be used
     */
    protected String notInstalled() {
        return Messages.get("missingPlugin.notInstalled", typeId);
    }

    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), typeId);
    }
}

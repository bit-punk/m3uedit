package org.bitpunk.m3uedit.plugins;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.bitpunk.m3uedit.api.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The state m3uedit keeps per plugin, which is not part of the plugin: whether it is enabled. Plugins are compared by
 * identity (see {@link Plugin}), so each instance has a state of its own; it is dropped with the plugin.
 */
public final class PluginStates {
    private static final Map<Plugin, BooleanProperty> ENABLED = Collections.synchronizedMap(new WeakHashMap<>());

    private PluginStates() {
        // Utility class
    }

    /**
     * @return whether the plugin is enabled, false for a new plugin; stored in the project-file
     */
    public static BooleanProperty enabled(Plugin plugin) {
        return ENABLED.computeIfAbsent(plugin, key -> new SimpleBooleanProperty(false));
    }
}

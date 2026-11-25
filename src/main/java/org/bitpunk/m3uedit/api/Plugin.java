package org.bitpunk.m3uedit.api;

import javafx.stage.Window;

/**
 * The common part of all plugins: inputs, filters, sorters, enrichers and outputs.
 * <p>
 * A plugin is found by the {@link java.util.ServiceLoader} (a file {@code META-INF/services/<kind>} listing its
 * class), needs a public constructor without arguments and declares its identity by {@link PluginType}. m3uedit creates
 * an instance for each use in a project, calls {@link #init(PluginContext)} once and then {@link #setSettings(Record)}
 * with the settings stored in the project-file. Plugins are compared by identity, so they must not override
 * {@code equals} and {@code hashCode}.
 * <p>
 * Whether a plugin is enabled is managed by m3uedit, not by the plugin.
 */
public interface Plugin extends NamedItem {

    /**
     * Called once after the plugin has been created, before any other method except {@link #getSettingsType()}.
     *
     * @param context what the plugin gets from m3uedit, e.g. its texts; to be kept by the plugin
     */
    default void init(PluginContext context) {
    }

    /**
     * @return the name shown to the user, in the language of the user interface
     */
    @Override
    String getName();

    /**
     * @return a short description shown to the user, in the language of the user interface
     */
    String getDescription();

    /**
     * @return the class of the settings of this plugin: a record of plain Java types (strings, numbers, booleans,
     * enums, lists, maps and records of these), stored in the project-file; {@link NoSettings} if it has none
     */
    default Class<? extends Record> getSettingsType() {
        return NoSettings.class;
    }

    /**
     * @return the current settings, an instance of {@link #getSettingsType()}, stored when the project is saved
     */
    default Record getSettings() {
        return NoSettings.INSTANCE;
    }

    /**
     * Sets the settings read from a project-file. Settings missing in the file (e.g. written by an older version of
     * the plugin) are {@code null}, {@code 0} or {@code false}: the plugin replaces them by its defaults.
     *
     * @param settings an instance of {@link #getSettingsType()}
     */
    default void setSettings(Record settings) {
    }

    /**
     * @return true if the plugin can be configured by the user, see {@link #configure(Window)}
     */
    default boolean isConfigurable() {
        return false;
    }

    /**
     * Lets the user configure the plugin, e.g. in a dialog of its own. Called on the JavaFX application thread, never
     * in the command-line mode.
     *
     * @param owner the window the dialog belongs to, null if unknown
     * @return true if the user confirmed the settings, false if the user cancelled (the settings are unchanged then)
     */
    default boolean configure(Window owner) {
        return false;
    }
}

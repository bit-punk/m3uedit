package org.bitpunk.m3uedit.plugins;

import java.util.List;

public interface PluginService<T> {
    /**
     * Retrieves all the plugins.
     *
     * @return a List of plugins.
     */
    List<T> getAllPlugins();

    /**
     * Retrieves the names of all the plugins.
     *
     * @return a List of plugin names.
     */
    List<String> getAllPluginNames();

    /**
     * Retrieves the classes of all the plugins, without instantiating them.
     *
     * @return a List of plugin classes.
     */
    List<Class<? extends T>> getAllPluginTypes();
}
package org.bitpunk.m3uedit.plugins;

import java.util.List;

/**
 * The plugins of one kind, e.g. all inputs.
 *
 * @param <T> the kind of plugins
 */
public interface PluginService<T extends Plugin> {

    /**
     * @return the plugins, known without creating them, in the order they have been found
     */
    List<PluginDescriptor<T>> getDescriptors();

    /**
     * @return a new instance of each plugin
     */
    List<T> getAllPlugins();
}

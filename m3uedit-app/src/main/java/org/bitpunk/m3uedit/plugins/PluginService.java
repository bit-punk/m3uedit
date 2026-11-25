package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Plugin;
import java.util.List;
import java.util.Optional;

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

    /**
     * @return the plugin with the given type id, empty if there is none
     */
    default Optional<PluginDescriptor<T>> find(String id) {
        return getDescriptors().stream().filter(descriptor -> descriptor.id().equals(id)).findFirst();
    }

    /**
     * @return the plugin of the given class, empty if it is not one of these plugins
     */
    default Optional<PluginDescriptor<T>> find(Class<?> type) {
        return getDescriptors().stream().filter(descriptor -> descriptor.type() == type).findFirst();
    }
}

package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Plugin;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.ServiceLoader;

/**
 * The plugins of one kind (e.g. all inputs), found by a {@link ServiceLoader} via the files in
 * {@code META-INF/services}.
 *
 * @param <T> the kind of plugins
 */
@Slf4j
public abstract class AbstractPluginService<T extends Plugin> implements PluginService<T> {

    private final List<PluginDescriptor<T>> descriptors;

    /**
     * @param loader finds the plugins; a plugin with the type id of an earlier one is ignored and reported
     */
    protected AbstractPluginService(ServiceLoader<T> loader) {
        this.descriptors = List.copyOf(PluginDescriptor.withUniqueIds(
                loader.stream().map(PluginDescriptor::of).toList(), log::error));
    }

    @Override
    public List<PluginDescriptor<T>> getDescriptors() {
        return descriptors;
    }

    @Override
    public List<T> getAllPlugins() {
        return descriptors.stream().map(PluginDescriptor::create).toList();
    }
}

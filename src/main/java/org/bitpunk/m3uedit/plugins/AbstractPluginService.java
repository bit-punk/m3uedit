package org.bitpunk.m3uedit.plugins;

import java.util.List;
import java.util.ServiceLoader;

/**
 * This abstract class represents a service that handles all plugin-related tasks.
 * @param <T> The type of plugins that the service will handle.
 */
public abstract class AbstractPluginService<T extends Plugin> implements PluginService<T> {

    private final ServiceLoader<T> loader;

    /**
     * Constructs a new AbstractPluginService instance.
     *
     * @param loader The service loader used to load plugins.
     */
    protected AbstractPluginService(ServiceLoader<T> loader) {
        this.loader = loader;
    }

    /**
     * Returns all plugins loaded by this service.
     *
     * @return A list containing all loaded plugins.
     */
    @Override
    public List<T> getAllPlugins() {
        return loader
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    /**
     * Returns the names of all plugins loaded by this service.
     *
     * @return A list containing the names of all loaded plugins.
     */
    @Override
    public List<String> getAllPluginNames() {
        return loader
                .stream()
                .map(serviceProvider -> serviceProvider.get().getName())
                .toList();
    }

    /**
     * Returns the classes of all plugins loaded by this service, without instantiating them.
     *
     * @return A list containing the classes of all loaded plugins.
     */
    @Override
    public List<Class<? extends T>> getAllPluginTypes() {
        return loader
                .stream()
                .<Class<? extends T>>map(ServiceLoader.Provider::type)
                .toList();
    }
}

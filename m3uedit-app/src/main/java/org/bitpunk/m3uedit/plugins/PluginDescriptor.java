package org.bitpunk.m3uedit.plugins;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.api.PluginType;

import java.util.*;
import java.util.function.Consumer;

/**
 * A plugin found by a {@link java.util.ServiceLoader}, known without creating it: its type id, its class and its
 * name. New instances are created by {@link #create()}.
 *
 * @param id       the type id, stored in project-files, see {@link PluginType}
 * @param type     the class of the plugin
 * @param provider creates new instances
 * @param <T>      the kind of plugin, e.g. {@link org.bitpunk.m3uedit.api.Input}
 */
@Slf4j
public record PluginDescriptor<T extends Plugin>(String id, Class<? extends T> type,
                                                 ServiceLoader.Provider<T> provider) {

    public PluginDescriptor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(provider);
    }

    /**
     * Describes the plugin of the provider by its {@link PluginType}; a plugin without it gets its simple class-name
     * as id, which is the id plugins were stored with before.
     */
    public static <T extends Plugin> PluginDescriptor<T> of(ServiceLoader.Provider<T> provider) {
        PluginType pluginType = provider.type().getAnnotation(PluginType.class);
        String id = pluginType != null && !pluginType.id().isBlank() ? pluginType.id()
                : provider.type().getSimpleName();
        if (pluginType == null) {
            log.warn("plugin {} has no @PluginType, its simple class-name is used as type id", provider.type().getName());
        }
        return new PluginDescriptor<>(id, provider.type(), provider);
    }

    /**
     * @return a new instance of the plugin, initialised with its context
     */
    public T create() {
        T plugin = provider.get();
        plugin.init(new DefaultPluginContext(type));
        return plugin;
    }

    /**
     * @return the name shown to the user, in the language of the user interface; read without creating the plugin
     * if it declares the key of its name
     */
    public String name() {
        PluginType pluginType = type.getAnnotation(PluginType.class);
        if (pluginType != null && !pluginType.nameKey().isBlank()) {
            // from the texts of the plugin
            return new DefaultPluginContext(type).text(pluginType.nameKey());
        }
        return create().getName();
    }

    /**
     * Keeps the first plugin of each type id: two plugins with the same id could not be told apart in a project-file.
     *
     * @param errors receives a message for each plugin ignored
     * @return the plugins with unique ids, in their order
     */
    public static <D extends PluginDescriptor<?>> List<D> withUniqueIds(List<D> descriptors, Consumer<String> errors) {
        Map<String, D> byId = new LinkedHashMap<>();
        for (D descriptor : descriptors) {
            D first = byId.putIfAbsent(descriptor.id(), descriptor);
            if (first != null && first.type() != descriptor.type()) {
                errors.accept("plugin %s is ignored: its type id '%s' is already used by %s"
                        .formatted(descriptor.type().getName(), descriptor.id(), first.type().getName()));
            }
        }
        return new ArrayList<>(byId.values());
    }
}

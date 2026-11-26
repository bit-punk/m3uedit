package org.bitpunk.m3uedit.plugins;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.i18n.Messages;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The plugins of one kind (e.g. all inputs), found by a {@link ServiceLoader} via the files in
 * {@code META-INF/services}: first the built-in plugins, then those of the jars installed by the user
 * ({@link PluginJars}).
 *
 * @param <T> the kind of plugins
 */
@Slf4j
public abstract class AbstractPluginService<T extends Plugin> implements PluginService<T> {

    private final List<PluginDescriptor<T>> descriptors;

    /**
     * @param kind the interface of the plugins, e.g. {@code Input.class}
     * @param jars the jars installed by the user; a plugin with the type id of an earlier one (e.g. of a built-in
     *             plugin) is ignored and reported
     */
    protected AbstractPluginService(Class<T> kind, PluginJars jars) {
        List<PluginDescriptor<T>> found = new ArrayList<>();
        ClassLoader builtIn = AbstractPluginService.class.getClassLoader();
        ServiceLoader.load(kind, builtIn).stream().map(PluginDescriptor::of).forEach(found::add);
        for (PluginJars.Jar jar : jars.getJars()) {
            found.addAll(pluginsOf(kind, jar, jars));
        }
        this.descriptors = List.copyOf(PluginDescriptor.withUniqueIdsReporting(found, (ignored, message) -> {
            if (ignored.isInstalled()) {
                jars.report(ignored.jar(), Messages.get("pluginJars.problem.idUsed", ignored.type().getName(),
                        ignored.id()));
            } else {
                log.error(message);
            }
        }));
    }

    /**
     * @return a service for the given kind of plugins and jars, e.g. for tests
     */
    public static <T extends Plugin> PluginService<T> of(Class<T> kind, PluginJars jars) {
        return new AbstractPluginService<>(kind, jars) {
        };
    }

    /**
     * @return the plugins of the jar that can be created; the others are reported
     */
    private static <T extends Plugin> List<PluginDescriptor<T>> pluginsOf(Class<T> kind, PluginJars.Jar jar,
                                                                         PluginJars jars) {
        List<PluginDescriptor<T>> plugins = new ArrayList<>();
        Iterator<ServiceLoader.Provider<T>> providers = ServiceLoader.load(kind, jar.classLoader()).stream().iterator();
        while (true) {
            ServiceLoader.Provider<T> provider;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                provider = providers.next();
            } catch (ServiceConfigurationError e) {
                // e.g. a class named in META-INF/services that is missing or not a plugin of this kind
                jars.report(jar.file(), Messages.get("pluginJars.problem.cannotLoad", e.getMessage()));
                continue;
            }
            // the class loader of the jar also finds the built-in plugins of its parent
            if (provider.type().getClassLoader() != jar.classLoader()) {
                continue;
            }
            PluginDescriptor<T> descriptor = PluginDescriptor.of(provider, jar.file());
            try {
                // created once, so a plugin that cannot be created is not offered
                descriptor.create().getName();
                plugins.add(descriptor);
            } catch (RuntimeException | LinkageError e) {
                log.warn("plugin {} of {} cannot be created", provider.type().getName(), jar.file(), e);
                jars.report(jar.file(), Messages.get("pluginJars.problem.cannotCreate", provider.type().getName(),
                        String.valueOf(e)));
            }
        }
        return plugins;
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

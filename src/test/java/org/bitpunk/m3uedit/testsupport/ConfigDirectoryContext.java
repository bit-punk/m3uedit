package org.bitpunk.m3uedit.testsupport;

import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * The context of a plugin in tests reading and writing its configuration-files in a directory of the test, so tests
 * with their own configuration do not change the one of other tests.
 */
public class ConfigDirectoryContext extends DefaultPluginContext {
    private final Path configDirectory;

    public ConfigDirectoryContext(Class<?> pluginType, Path configDirectory) {
        super(pluginType);
        this.configDirectory = configDirectory;
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }

    /**
     * @param constructor     e.g. {@code ChannelNameFilter::new}
     * @param configDirectory the directory of the configuration-files of the plugin
     * @return the plugin, initialised with a context using the given configuration directory
     */
    public static <T extends Plugin> T create(Supplier<T> constructor, Path configDirectory) {
        T plugin = constructor.get();
        plugin.init(new ConfigDirectoryContext(plugin.getClass(), configDirectory));
        return plugin;
    }
}

package org.bitpunk.m3uedit.testsupport;

import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.PluginStates;

import java.util.function.Supplier;

/**
 * Creates plugins in tests like m3uedit does: initialised with their context.
 */
public final class TestPluginSupport {

    private TestPluginSupport() {
        // Utility class
    }

    /**
     * @param constructor e.g. {@code FileInput::new}
     * @return the plugin, initialised with its context
     */
    public static <T extends Plugin> T create(Supplier<T> constructor) {
        T plugin = constructor.get();
        plugin.init(new DefaultPluginContext(plugin.getClass()));
        return plugin;
    }

    /**
     * @param plugin e.g. an anonymous subclass of a plugin
     * @return the plugin, initialised with its context
     */
    public static <T extends Plugin> T init(T plugin) {
        plugin.init(new DefaultPluginContext(plugin.getClass()));
        return plugin;
    }

    /**
     * @return the plugin, initialised with its context and enabled
     */
    public static <T extends Plugin> T enabled(Supplier<T> constructor) {
        T plugin = create(constructor);
        PluginStates.enabled(plugin).set(true);
        return plugin;
    }
}

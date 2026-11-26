package org.bitpunk.m3uedit.plugins;

import java.util.List;

/**
 * All plugin services, the built-in plugins and those installed by the user.
 */
public final class PluginServices {

    private PluginServices() {
        // Utility class
    }

    /**
     * @return the services of all kinds of plugins: inputs, filters, sorters, enrichers, outputs
     */
    public static List<PluginService<?>> services() {
        return List.of(InputPluginService.getInstance(), FilterPluginService.getInstance(),
                SorterPluginService.getInstance(), EnricherPluginService.getInstance(),
                OutputPluginService.getInstance());
    }

    /**
     * @return all plugins; once called, the problems of the installed jars are complete
     */
    public static List<PluginDescriptor<?>> all() {
        return services().stream().<PluginDescriptor<?>>flatMap(service -> service.getDescriptors().stream()).toList();
    }
}

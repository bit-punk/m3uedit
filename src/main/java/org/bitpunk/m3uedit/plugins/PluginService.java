package org.bitpunk.m3uedit.plugins;

import java.util.List;

public interface PluginService {
    List<? extends Plugin> getAllPlugins();
    List<String> getAllPluginNames();
    Plugin getPlugin(String pluginName);
}
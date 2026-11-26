package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.Plugin;


public class OutputPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private OutputPluginService(Class<T> kind, PluginJars jars) {
        super(kind, jars);
    }

    public static OutputPluginService<Output> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final OutputPluginService<Output> INSTANCE = new OutputPluginService<>(Output.class, PluginJars.installed());
    }
}
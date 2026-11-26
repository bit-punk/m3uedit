package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.api.Sorter;


public class SorterPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private SorterPluginService(Class<T> kind, PluginJars jars) {
        super(kind, jars);
    }

    public static SorterPluginService<Sorter> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final SorterPluginService<Sorter> INSTANCE = new SorterPluginService<>(Sorter.class, PluginJars.installed());
    }
}
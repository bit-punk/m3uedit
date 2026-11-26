package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.Plugin;


public class FilterPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private FilterPluginService(Class<T> kind, PluginJars jars) {
        super(kind, jars);
    }

    public static FilterPluginService<Filter> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final FilterPluginService<Filter> INSTANCE = new FilterPluginService<>(Filter.class, PluginJars.installed());
    }
}
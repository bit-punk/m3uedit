package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.ServiceLoader;

public class FilterPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private FilterPluginService(ServiceLoader<T> loader) {
        super(loader);
    }

    public static FilterPluginService<Filter> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final FilterPluginService<Filter> INSTANCE = new FilterPluginService<>(ServiceLoader.load(Filter.class));
    }
}
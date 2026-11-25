package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.api.Sorter;

import java.util.ServiceLoader;

public class SorterPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private SorterPluginService(ServiceLoader<T> loader) {
        super(loader);
    }

    public static SorterPluginService<Sorter> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final SorterPluginService<Sorter> INSTANCE = new SorterPluginService<>(ServiceLoader.load(Sorter.class));
    }
}
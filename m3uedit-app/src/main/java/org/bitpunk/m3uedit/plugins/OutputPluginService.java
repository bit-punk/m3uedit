package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.Plugin;

import java.util.ServiceLoader;

public class OutputPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private OutputPluginService(ServiceLoader<T> loader) {
        super(loader);
    }

    public static OutputPluginService<Output> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final OutputPluginService<Output> INSTANCE = new OutputPluginService<>(ServiceLoader.load(Output.class));
    }
}
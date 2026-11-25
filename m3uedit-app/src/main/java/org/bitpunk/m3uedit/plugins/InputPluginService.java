package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Plugin;

import java.util.ServiceLoader;

public class InputPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private InputPluginService(ServiceLoader<T> loader) {
        super(loader);
    }

    public static InputPluginService<Input> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final InputPluginService<Input> INSTANCE = new InputPluginService<>(ServiceLoader.load(Input.class));
    }
}
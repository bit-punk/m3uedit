package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Plugin;


public class InputPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private InputPluginService(Class<T> kind, PluginJars jars) {
        super(kind, jars);
    }

    public static InputPluginService<Input> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final InputPluginService<Input> INSTANCE = new InputPluginService<>(Input.class, PluginJars.installed());
    }
}
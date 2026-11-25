package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Plugin;

import java.util.ServiceLoader;

public class EnricherPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private EnricherPluginService(ServiceLoader<T> loader) {
        super(loader);
    }

    public static EnricherPluginService<Enricher> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final EnricherPluginService<Enricher> INSTANCE = new EnricherPluginService<>(ServiceLoader.load(Enricher.class));
    }
}

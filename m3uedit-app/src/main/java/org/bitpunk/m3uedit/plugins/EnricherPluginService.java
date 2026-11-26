package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Plugin;


public class EnricherPluginService<T extends Plugin> extends AbstractPluginService<T> {

    private EnricherPluginService(Class<T> kind, PluginJars jars) {
        super(kind, jars);
    }

    public static EnricherPluginService<Enricher> getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final EnricherPluginService<Enricher> INSTANCE = new EnricherPluginService<>(Enricher.class, PluginJars.installed());
    }
}

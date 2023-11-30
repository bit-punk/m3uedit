package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.sorter.Sorter;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class SorterPluginService implements PluginService {
    private static SorterPluginService sorterPluginService;
    private final ServiceLoader<Sorter> loader;

    private SorterPluginService() {
        this.loader = ServiceLoader.load(Sorter.class);
    }

     public static synchronized SorterPluginService getInstance() {
        if (sorterPluginService == null) {
            sorterPluginService = new SorterPluginService();
        }
        return sorterPluginService;
    }

    @Override
    public List<Sorter> getAllPlugins() {
        List<Sorter> sorterPlugins = new ArrayList<>();
        for (Sorter currentSorter : loader) {
            sorterPlugins.add(currentSorter);
        }
        return sorterPlugins;
    }

    @Override
    public List<String> getAllPluginNames() {
        List<String> sorterNames = new ArrayList<>();
        for (Sorter currentSorter : loader) {
            sorterNames.add(currentSorter.getName());
        }
        return sorterNames;
    }

    @Override
    public Sorter getPlugin(String pluginName) {
        throw new UnsupportedOperationException("method not implemented");
    }

}
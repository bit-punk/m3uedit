package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.sorter.Sorter;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class SorterPluginService {
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

    public List<Sorter> getSorterPlugins() {
        List<Sorter> sorterPlugins = new ArrayList<>();
        for (Sorter currentSorter : loader) {
            sorterPlugins.add(currentSorter);
        }
        return sorterPlugins;
    }

}
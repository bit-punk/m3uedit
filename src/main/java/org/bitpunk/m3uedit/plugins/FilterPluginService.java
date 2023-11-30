package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class FilterPluginService implements PluginService {
    private static FilterPluginService filterPluginService;
    private final ServiceLoader<Filter> loader;


    private FilterPluginService() {
        this.loader = ServiceLoader.load(Filter.class);
    }

    public static synchronized FilterPluginService getInstance() {
        if (filterPluginService == null) {
            filterPluginService = new FilterPluginService();
        }
        return filterPluginService;
    }

    @Override
    public List<Filter> getAllPlugins() {
        List<Filter> filterPlugins = new ArrayList<>();
        for (Filter currentFilter : loader) {
            filterPlugins.add(currentFilter);
        }
        return filterPlugins;
    }

    @Override
    public List<String> getAllPluginNames() {
        List<String> filterNames = new ArrayList<>();
        for (Filter currentFilter : loader) {
            filterNames.add(currentFilter.getName());
        }
        return filterNames;
    }

}

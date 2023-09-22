package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.*;

public class FilterPluginService {
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

    public Optional<String> getFilterDescription(String name) {
        String filterDescription = null;
        Iterator<Filter> filterIterator = loader.iterator();
        while (filterDescription == null && filterIterator.hasNext()) {
            Filter currentFilter = filterIterator.next();
            filterDescription = currentFilter.getDescription();
        }
        return Optional.ofNullable(filterDescription);
    }

    public List<String> getFilterNames() {
        List<String> filterNames = new ArrayList<>();
        for (Filter currentFilter : loader) {
            filterNames.add(currentFilter.getName());
        }
        return filterNames;
    }

}

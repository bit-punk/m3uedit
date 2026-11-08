package org.bitpunk.m3uedit.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.SorterPluginService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class DataModelFactory {

    private DataModelFactory() {
        // hide constructor of this util-class
    }

    public static DataModel createDataModel(String name) {
        DataModel dataModel = new DataModel(name);
        dataModel.setFilters(FXCollections.observableArrayList(FilterPluginService.getInstance().getAllPlugins()));
        dataModel.setSorters(FXCollections.observableArrayList(SorterPluginService.getInstance().getAllPlugins()));
        return dataModel;
    }

    /**
     * Makes sure a project contains each available filter and sorter exactly once: plugins added to the application
     * after the project has been saved are appended (disabled), duplicates of a plugin-type are dropped.
     * The order of the plugins already contained in the project is kept.
     */
    public static void completePlugins(DataModel dataModel) {
        completePlugins(dataModel.getFilters(), FilterPluginService.getInstance().getAllPlugins());
        completePlugins(dataModel.getSorters(), SorterPluginService.getInstance().getAllPlugins());
    }

    // package-private for tests
    static <T extends Plugin> void completePlugins(ObservableList<T> plugins, List<T> availablePlugins) {
        Set<Class<?>> types = new HashSet<>();
        List<T> completed = new ArrayList<>();
        for (T plugin : plugins) {
            if (types.add(plugin.getClass())) {
                completed.add(plugin);
            } else {
                log.warn("dropping duplicate plugin {} from project", plugin);
            }
        }
        for (T available : availablePlugins) {
            if (types.add(available.getClass())) {
                completed.add(available);
            }
        }
        if (!completed.equals(plugins)) {
            plugins.setAll(completed);
        }
    }
}

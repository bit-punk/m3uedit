package org.bitpunk.m3uedit.model;

import javafx.collections.FXCollections;
import org.bitpunk.m3uedit.plugins.FilterPluginService;

public class DataModelFactory {
    public static DataModel createDataModel(String name) {
        DataModel dataModel = new DataModel(name);
        dataModel.setFilters(FXCollections.observableArrayList(FilterPluginService.getInstance().getAllPlugins()));
        return dataModel;
    }
}

package org.bitpunk.m3uedit.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FilterController extends VBox implements Initializable, ModelAware {
    private static final Logger log = LoggerFactory.getLogger(FilterController.class);
    private DataModel dataModel;

    @FXML
    public ListView<Filter> availableFiltersListView;

    public FilterController() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("fxml/Filter.fxml"));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    private void initAvailableFilterListView() {
        setCellFactory(availableFiltersListView, dataModel.getFilters());
        List<Filter> filterPlugins = FilterPluginService.getInstance().getFilterPlugins();
        log.debug("filterNames: {}", filterPlugins);
        availableFiltersListView.getItems().addAll(filterPlugins);
    }

    // ToDo duplicate code
    private void setCellFactory(ListView<Filter> availableFiltersListView, ObservableList<Filter> selectedItems) {
        availableFiltersListView.setCellFactory(CheckBoxListCell.forListView(item -> {
            BooleanProperty observable = new SimpleBooleanProperty();
            observable.addListener((obs, wasSelected, isNowSelected) -> {
                log.debug("Check box for {} changed from {} to {}", item, wasSelected, isNowSelected);
                if (isNowSelected) {
                    selectedItems.add(item);
                } else {
                    selectedItems.remove(item);
                }
            });
            return observable;
        }, new StringConverter<>() {
            @Override
            public String toString(Filter filter) {
                return filter.getName();
            }

            @Override
            public Filter fromString(String string) {
                return null;
            }
        }));
    }

    @Override
    public void initModel(DataModel dataModel) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Model can only be initialized once");
        }
        this.dataModel = dataModel;
        initAvailableFilterListView();
    }
}
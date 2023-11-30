package org.bitpunk.m3uedit.controller;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FilterController extends AbstractController {
    public static final String FXML_FILTER = "fxml/Filter.fxml";
    private static final Logger log = LoggerFactory.getLogger(FilterController.class);
    @FXML
    public ListView<Filter> availableFiltersListView;
    private DataModel dataModel;

    public FilterController() {
        super(FXML_FILTER);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    private void initAvailableFilterListView() {
        setCellFactory(availableFiltersListView);
        List<Filter> filterPlugins = FilterPluginService.getInstance().getAllPlugins();
        log.debug("filterNames: {}", filterPlugins);
        availableFiltersListView.getItems().addAll(filterPlugins);
    }

    private void initListener() {
        dataModel.getFilters().addListener((ListChangeListener<? super Filter>) listChangeListener -> filterEntries());
    }

    private void filterEntries() {
        dataModel.getFilters().forEach(filter -> filter.filter(dataModel.getEntries()));
    }

    private void setCellFactory(ListView<Filter> availableFiltersListView) {
        availableFiltersListView.setCellFactory((ListView<Filter> param) -> new ListCell<>() {
            private CheckBox checkBox;

            @Override
            public void updateItem(Filter filter, boolean empty) {
                super.updateItem(filter, empty);
                if (!(empty || filter == null)) {
                    getCheckBox().setSelected(dataModel.getFilters().stream().anyMatch(f -> f.getName().equals(filter.getName())));
                    setGraphic(getCheckBox());
                    setText(filter.getName());
                    setTooltip(new Tooltip(filter.getDescription()));
                } else {
                    setGraphic(null);
                    setText(null);
                }
            }

            /**
             * Returns a CheckBox object.
             * <p>
             * The method checks if the "checkBox" instance variable is null. If it is, a new CheckBox object is created.
             * The method also adds a listener to the selectedProperty of the CheckBox object. This listener updates the
             * dataModel's filters based on the selection state of the CheckBox.
             *
             * @return a CheckBox object.
             */
            private CheckBox getCheckBox() {
                if (checkBox == null) {
                    checkBox = new CheckBox();
                    checkBox.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                        if (getItem() != null) {
                            if (isNowSelected) {
                                dataModel.getFilters().add(getItem());
                            } else {
                                dataModel.getFilters().remove(getItem());
                            }
                        }
                        log.debug("Currently selected Filters: {}", dataModel.getFilters());
                    });
                }
                return checkBox;
            }

        });
    }

    @Override
    public void initModel(DataModel dataModel) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Model can only be initialized once");
        }
        this.dataModel = dataModel;
        initAvailableFilterListView();
        initListener();
    }
}
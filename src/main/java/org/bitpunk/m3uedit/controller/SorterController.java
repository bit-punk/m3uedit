package org.bitpunk.m3uedit.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.util.StringConverter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class SorterController extends AbstractController {
    private static final Logger log = LoggerFactory.getLogger(FilterController.class);
    public static final String FXML_SORTER = "fxml/Sorter.fxml";
    private DataModel dataModel;

    @FXML
    public ListView<Sorter> availableSortersListView;

    public SorterController() {
        super(FXML_SORTER);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    private void initAvailableSorterListView() {
        setCellFactory(availableSortersListView, dataModel.getSorters());
        List<Sorter> sorterPlugins = SorterPluginService.getInstance().getSorterPlugins();
        log.debug("sorter-plugins: {}", sorterPlugins);
        availableSortersListView.getItems().addAll(sorterPlugins);
    }

    private void setCellFactory(ListView<Sorter> availableFiltersListView, List<Sorter> selectedItems) {
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
            public String toString(Sorter sorter) {
                return sorter.getName();
            }

            @Override
            public Sorter fromString(String string) {
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
        initAvailableSorterListView();
    }
}
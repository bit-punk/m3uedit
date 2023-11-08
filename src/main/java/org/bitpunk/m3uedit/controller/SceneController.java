package org.bitpunk.m3uedit.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private final List<String> selectedSorterNames = new ArrayList<>();
    private final List<String> selectedFilterNames = new ArrayList<>();

    @FXML
    public Scene scene;
    @FXML
    public ListView<String> availableFiltersListView;
    @FXML
    public ListView<String> availableSortersListView;
    @FXML
    public EntryListController entryListController;
    @FXML
    public InputController inputController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initAvailableFilterListView();
        initAvailableSorterListView();
    }

    private void initAvailableFilterListView() {
        setCellFactory(availableFiltersListView, selectedFilterNames);
        List<String> filterNames = FilterPluginService.getInstance().getFilterNames();
        log.debug("filterNames: {}", filterNames);
        availableFiltersListView.getItems().addAll(filterNames);
    }

    private void initAvailableSorterListView() {
        setCellFactory(availableSortersListView, selectedSorterNames);
        List<String> sorterNames = SorterPluginService.getInstance().getSorterNames();
        log.debug("sorterNames: {}", sorterNames);
        availableSortersListView.getItems().addAll(sorterNames);
    }

    private void setCellFactory(ListView<String> availableFiltersListView, List<String> selectedItems) {
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
        }));
    }

    @FXML
    private void handleExitAction() {
        System.exit(0);
    }

    @FXML
    private void applySelectedFilterAction() {
        for (String selectedFilterName : selectedFilterNames) {
            Filter filter = FilterPluginService.getInstance().getFilter(selectedFilterName);
            ObservableList<Entry> filteredEntries = FXCollections.observableList(filter.filter(entryListController.getItems()));
            entryListController.setItems(filteredEntries);
        }
    }

    @FXML
    private void applySelectedSorterAction() {
        for (String selectedSorterName : selectedSorterNames) {
            Sorter sorter = SorterPluginService.getInstance().getSorter(selectedSorterName);
            ObservableList<Entry> sortedEntries = FXCollections.observableList(sorter.sort(entryListController.getItems()));
            entryListController.setItems(sortedEntries);
        }
    }

    @FXML
    @SuppressWarnings("unchecked")
    public void loadPlayListData() {
        ObservableList<Entry> dataFromInputSource = FXCollections.observableArrayList();
        for (Input currentInput : inputController.getSelectedInputs()) {
            log.debug("reading from: {}", currentInput);
            try {
                dataFromInputSource.addAll(currentInput.read());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        entryListController.setItems(dataFromInputSource);
    }
}
package org.bitpunk.m3uedit.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.cell.CheckBoxListCell;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private final List<String> selectedSorterNames = new ArrayList<>();
    public Tab inputTab;
    public Tab filterTab;
    public Tab outputTab;
    public Tab entryTab;

    private final DataModel dataModel = new DataModel();

    @FXML
    public Scene scene;
    @FXML
    public ListView<String> availableSortersListView;
    @FXML
    public EntryListController entryListController;
    @FXML
    public InputController inputController;
    @FXML
    public FilterController filterController;
    public Label statusLabel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initInputs();
        initFilters();
        initOutputs();
        initEntries();
        initAvailableSorterListView();
        initStatusLabel();
    }

    private void initFilters() {
        filterController = new FilterController();
        filterController.initModel(dataModel);
        filterTab.setContent(filterController);
    }

    private void initStatusLabel(){
        dataModel.getEntries().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatuslabel());
    }

    private void updateStatuslabel() {
        String statusMessage = String.format("%s entries loaded", dataModel.getEntries().size());
        statusLabel.setText(statusMessage);
    }

    private void initOutputs() {
        OutputController outputController = new OutputController();
        outputController.initModel(dataModel);
        outputTab.setContent(outputController);
    }

    private void initEntries() {
        entryListController = new EntryListController();
        entryListController.initModel(dataModel);
        entryTab.setContent(entryListController);
    }

    private void initInputs() {
        inputController = new InputController();
        inputController.initModel(dataModel);
        inputTab.setContent(inputController);
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

}
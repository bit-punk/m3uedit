package org.bitpunk.m3uedit.controller;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;

import java.net.URL;
import java.util.ResourceBundle;

public class SceneController implements Initializable {
    public Tab inputTab;
    public Tab filterTab;
    public Tab sorterTab;
    public Tab outputTab;
    public Tab entryTab;

    private final DataModel dataModel = new DataModel();

    @FXML
    public Scene scene;
    @FXML
    public EntryListController entryListController;
    @FXML
    public InputController inputController;
    @FXML
    public FilterController filterController;
    @FXML
    SorterController sorterController;
    public Label statusLabel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initInputs();
        initFilters();
        initSorters();
        initOutputs();
        initEntries();
        initStatusLabel();
    }

    private void initSorters() {
        sorterController = new SorterController();
        sorterController.initModel(dataModel);
        sorterTab.setContent(sorterController);
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

    @FXML
    private void handleExitAction() {
        System.exit(0);
    }

}
package org.bitpunk.m3uedit.controller;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;

import java.net.URL;
import java.util.ResourceBundle;

public class ProjectController extends AbstractController {
    public static final String FXML_PROJECT = "fxml/Project.fxml";
    public Tab inputTab;
    public Tab filterTab;
    public Tab sorterTab;
    public Tab entryTab;
    public Tab outputTab;
    @FXML
    public InputController inputController;
    @FXML
    public FilterController filterController;
    @FXML
    SorterController sorterController;
    @FXML
    public EntryListController entryListController;
    @Setter
    public Label statusLabel;
    @Getter
    private DataModel dataModel;

    public ProjectController(DataModel dataModel) {
        super(FXML_PROJECT);
        this.dataModel = dataModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    private void initInputs() {
        inputController = new InputController();
        inputController.initModel(dataModel);
        inputTab.setContent(inputController);
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

    private void initStatusLabel() {
        dataModel.getEntries().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
    }

    public void updateStatusLabel() {
        String statusMessage = String.format("%s entries loaded", dataModel.getEntries().size());
        statusLabel.setText(statusMessage);
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        // ToDo having to initialize components in the initModel method is weird ...
        initInputs();
        initFilters();
        initSorters();
        initOutputs();
        initEntries();
        initStatusLabel();
    }
}
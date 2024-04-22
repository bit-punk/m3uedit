package org.bitpunk.m3uedit.controller;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;

public class ProjectController extends AbstractController implements ModelAware {
    public static final String FXML_PROJECT = "fxml/Project.fxml";
    public Tab inputTab;
    public Tab rawEntriesTab;
    public Tab filterTab;
    public Tab filteredEntriesTab;
    public Tab sorterTab;
    public Tab entryTab;
    public Tab outputTab;
    @FXML
    public InputController inputController;
    @FXML
    public RawEntriesController rawEntriesController;
    @FXML
    public FilterController filterController;
    @FXML
    public FilteredEntriesController filteredEntriesController;
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

    private void initInputs(DataModel dataModel) {
        inputController = new InputController();
        inputController.initInputs(dataModel.getInputs());
        inputTab.setContent(inputController);
    }

    private void initRawEntries(DataModel dataModel) {
        rawEntriesController = new RawEntriesController();
        rawEntriesController.initModel(dataModel);
        rawEntriesTab.setContent(rawEntriesController);
    }

    private void initFilteredEntries(DataModel dataModel) {
        filteredEntriesController = new FilteredEntriesController();
        filteredEntriesController.initModel(dataModel);
        filteredEntriesTab.setContent(filteredEntriesController);
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
        dataModel.getSortedEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
    }

    public void updateStatusLabel() {
        String statusMessage = "%s entries loaded".formatted(dataModel.getSortedEntryList().size());
        statusLabel.setText(statusMessage);
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        // ToDo having to initialize components in the initModel method is weird ...
        initInputs(dataModel);
        initRawEntries(dataModel);
        initFilters();
        initFilteredEntries(dataModel);
        initSorters();
        initOutputs();
        initEntries();
        initStatusLabel();
    }
}
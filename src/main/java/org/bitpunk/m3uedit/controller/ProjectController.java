package org.bitpunk.m3uedit.controller;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;

public class ProjectController extends AbstractController implements ModelAware {
    public static final String FXML_PROJECT = "fxml/Project.fxml";
    @FXML
    public Tab inputTab;
    @FXML
    public Tab rawEntriesTab;
    @FXML
    public Tab filterTab;
    @FXML
    public Tab filteredEntriesTab;
    @FXML
    public Tab sorterTab;
    @FXML
    public Tab entryTab;
    @FXML
    public Tab outputTab;
    @FXML
    public InputController inputController;
    @FXML
    public RawEntriesController rawEntriesController;
    @FXML
    public FilterController filterController;
    @FXML
    private TabPane projectTabPane;
    @FXML
    public FilteredEntriesController filteredEntriesController;
    @FXML
    public SorterController sorterController;
    @FXML
    public EntryListController entryListController;
    @FXML
    public OutputController outputController;
    @Setter
    public Label statusLabel;
    @Getter
    private DataModel dataModel;

    public ProjectController(DataModel dataModel) {
        super(FXML_PROJECT);
        initModel(dataModel);
    }

    private void initStatusLabel() {
        dataModel.getRawEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
        dataModel.getFilteredEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
        dataModel.getSortedEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());

        projectTabPane.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldTab, newTab) -> updateStatusLabel());
    }

    public void updateStatusLabel() {
        if (statusLabel == null) {
            return;
        }

        int entryCount = getSelectedEntryCount();
        String statusMessage = "%s entries loaded".formatted(entryCount);
        statusLabel.setText(statusMessage);
    }

    private int getSelectedEntryCount() {
        Tab selectedTab = projectTabPane.getSelectionModel().getSelectedItem();

        if (selectedTab == rawEntriesTab) {
            return dataModel.getRawEntryList().size();
        }
        if (selectedTab == filteredEntriesTab) {
            return dataModel.getFilteredEntryList().size();
        }

        return dataModel.getSortedEntryList().size();
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        inputController.initModel(dataModel);
        rawEntriesController.initModel(dataModel);
        filterController.initModel(dataModel);
        filteredEntriesController.initModel(dataModel);
        sorterController.initModel(dataModel);
        outputController.initModel(dataModel);
        entryListController.initModel(dataModel);
        initStatusLabel();
    }
}
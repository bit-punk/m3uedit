package org.bitpunk.m3uedit.controller;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import lombok.Getter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;

import java.net.URL;
import java.util.ResourceBundle;

public class ProjectController extends AbstractController implements Initializable, ModelAware {
    static final String LOADING_MESSAGE = "Loading entries ...";
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
    @Getter
    private Label statusLabel;
    @Getter
    private DataModel dataModel;
    private ProgressIndicator loadingIndicator;

    public ProjectController(DataModel dataModel) {
        super(FXML_PROJECT);
        initModel(dataModel);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        projectTabPane.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldTab, newTab) -> updateStatusLabel());
    }

    public void setStatusLabel(Label statusLabel) {
        this.statusLabel = statusLabel;
        updateStatusLabel();
    }

    private void initModelListeners() {
        dataModel.getRawEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
        dataModel.getFilteredEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
        dataModel.getSortedEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
        dataModel.busyProperty().addListener((observable, wasBusy, isBusy) -> updateStatusLabel());
        dataModel.setErrorHandler(ProjectController::showError);
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();   // not blocking, the error is reported asynchronously
    }

    public void updateStatusLabel() {
        if (statusLabel == null) {
            return;
        }
        // the status label is shared by all project tabs, only the selected project may update it
        if (isProjectTabSelected() == Boolean.FALSE) {
            return;
        }
        if (dataModel.isBusy()) {
            statusLabel.setText(LOADING_MESSAGE);
            statusLabel.setGraphic(getLoadingIndicator());
            return;
        }
        statusLabel.setGraphic(null);
        int entryCount = getSelectedEntryCount();
        String statusMessage = "%s entries loaded".formatted(entryCount);
        statusLabel.setText(statusMessage);
    }

    /**
     * @return whether the tab containing this project is selected, or null if this project is not shown in a tab
     */
    private Boolean isProjectTabSelected() {
        if (getScene() == null || getParent() == null) {
            return null;
        }
        TabPane sceneTabPane = (TabPane) getScene().lookup("#tabPane");
        if (sceneTabPane == null) {
            return null;
        }
        Tab selectedProjectTab = sceneTabPane.getSelectionModel().getSelectedItem();
        return selectedProjectTab != null && selectedProjectTab.getContent() == this;
    }

    private ProgressIndicator getLoadingIndicator() {
        if (loadingIndicator == null) {
            loadingIndicator = new ProgressIndicator();
            loadingIndicator.setPrefSize(14, 14);
            loadingIndicator.setMaxSize(14, 14);
        }
        return loadingIndicator;
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
        initModelListeners();
    }
}
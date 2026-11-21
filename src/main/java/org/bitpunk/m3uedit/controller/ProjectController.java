package org.bitpunk.m3uedit.controller;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.output.Output;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * A project: the pipeline on the left, the resulting playlist and the diff of the selected step on the right.
 */
public class ProjectController extends AbstractController implements Initializable, ModelAware {
    public static final String FXML_PROJECT = "fxml/Project.fxml";
    @FXML
    public SplitPane splitPane;
    @FXML
    public VBox pipelinePane;
    @FXML
    public ScrollPane pipelineScrollPane;
    @FXML
    public PipelineController pipelineController;
    @FXML
    public PipelineSummaryView summaryView;
    @FXML
    public TabPane resultTabPane;
    @FXML
    public Tab playlistTab;
    @FXML
    public Tab diffTab;
    @FXML
    public EntryListController entryListController;
    @FXML
    public DiffController diffController;
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
        // the pipeline keeps its width when the window is resized
        SplitPane.setResizableWithParent(pipelinePane, false);
    }

    public void setStatusLabel(Label statusLabel) {
        this.statusLabel = statusLabel;
        updateStatusLabel();
    }

    private void initModelListeners() {
        dataModel.getSortedEntryList().addListener((ListChangeListener<? super Entry>) listChangeListener -> updateStatusLabel());
        dataModel.busyProperty().addListener((observable, wasBusy, isBusy) -> updateStatusLabel());
        dataModel.savingProperty().addListener((observable, wasSaving, isSaving) -> updateStatusLabel());
        dataModel.setErrorHandler(ProjectController::showError);
    }

    /**
     * Selecting a step shows its effect in the diff, selecting an output shows the playlist it receives.
     */
    private void initSelectionListener() {
        pipelineController.getSelectedStep().addListener((observable, oldStep, newStep) -> {
            if (newStep instanceof Output) {
                resultTabPane.getSelectionModel().select(playlistTab);
            } else if (newStep != null) {
                resultTabPane.getSelectionModel().select(diffTab);
            }
        });
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Messages.get("common.error"));
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
        if (dataModel.isBusy() || dataModel.isSaving()) {
            statusLabel.setText(dataModel.isSaving() ? savingMessage() : loadingMessage());
            statusLabel.setGraphic(getLoadingIndicator());
            return;
        }
        statusLabel.setGraphic(null);
        statusLabel.setText(Messages.get("project.status.entries", dataModel.getSortedEntryList().size()));
    }

    /**
     * @return the status shown while the entries are loaded
     */
    static String loadingMessage() {
        return Messages.get("project.status.loading");
    }

    /**
     * @return the status shown while the playlist is written
     */
    static String savingMessage() {
        return Messages.get("project.status.saving");
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

    /**
     * @return the step selected in the pipeline, null if none
     */
    public Plugin getSelectedStep() {
        return pipelineController.getSelectedStep().get();
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        pipelineController.initModel(dataModel);
        // fixed below the scrolled pipeline, so it is always visible
        pipelinePane.getChildren().add(pipelineController.getWriteBar());
        summaryView.initModel(dataModel, pipelineController.getSelectedStep());
        diffController.initModel(dataModel, pipelineController.getSelectedStep());
        entryListController.initModel(dataModel);
        initModelListeners();
        initSelectionListener();
    }
}

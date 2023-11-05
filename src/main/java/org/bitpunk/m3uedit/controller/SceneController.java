package org.bitpunk.m3uedit.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputFactory;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.OutputFactory;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private final List<String> selectedSorterNames = new ArrayList<>();
    private final List<String> selectedFilterNames = new ArrayList<>();
    @FXML
    public Button saveFileButton;
    @FXML
    public Scene scene;
    @FXML
    public ListView<String> availableFiltersListView;
    @FXML
    public ComboBox<String> addInputComboBox;
    @FXML
    public ComboBox<String> addOutputComboBox;
    @FXML
    public TableView<Input> inputTableView;
    @FXML
    public TableView<Output> outputTableView;
    @FXML
    public ListView<String> availableSortersListView;
    @FXML
    public EntryListController entryList;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initInputTableView();
        initOutputTableView();
        initAvailableInputComboBox();
        initAvailableFilterListView();
        initAvailableSorterListView();
        initAvailableOutputComboBox();
    }

    @SuppressWarnings("unchecked")
    private void initInputTableView() {
        TableColumn<Input, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Input, String> sourceColumn = new TableColumn<>("Source");
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("source"));
        inputTableView.getColumns().addAll(nameColumn, sourceColumn);
        inputTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @SuppressWarnings("unchecked")
    private void initOutputTableView() {
        TableColumn<Output, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Output, String> sourceColumn = new TableColumn<>("Destination");
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("output"));
        outputTableView.getColumns().addAll(nameColumn, sourceColumn);
        outputTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void initAvailableInputComboBox() {
        List<String> inputNames = InputPluginService.getInstance().getInputNames();
        log.debug("inputNames: {}", inputNames);
        addInputComboBox.getItems().add("add Input");
        addInputComboBox.getItems().addAll(inputNames);
        addInputComboBox.getSelectionModel().select(0);
    }

    private void initAvailableOutputComboBox() {
        List<String> outputNames = OutputPluginService.getInstance().getOutputNames();
        log.debug("outputNames: {}", outputNames);
        addOutputComboBox.getItems().add("add Output");
        addOutputComboBox.getItems().addAll(outputNames);
        addOutputComboBox.getSelectionModel().select(0);
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
    private void saveListToOutputs() {
        ObservableList<Output> outputs = outputTableView.getItems();
        for (Output output : outputs) {
            output.write(entryList.getItems());
        }
    }

    @FXML
    private void handleExitAction() {
        System.exit(0);
    }

    @FXML
    private void applySelectedFilterAction() {
        for (String selectedFilterName : selectedFilterNames) {
            Filter filter = FilterPluginService.getInstance().getFilter(selectedFilterName);
            ObservableList<Entry> filteredEntries = FXCollections.observableList(filter.filter(entryList.getItems()));
            entryList.setItems(filteredEntries);
        }
    }

    @FXML
    private void applySelectedSorterAction() {
        for (String selectedSorterName : selectedSorterNames) {
            Sorter sorter = SorterPluginService.getInstance().getSorter(selectedSorterName);
            ObservableList<Entry> sortedEntries = FXCollections.observableList(sorter.sort(entryList.getItems()));
            entryList.setItems(sortedEntries);
        }
    }

    @FXML
    private void addInputAction(ActionEvent actionEvent) {
        log.debug("addInputAction: {}", actionEvent);
        String selectedInputName = addInputComboBox.getSelectionModel().getSelectedItem();
        Input input = InputFactory.getInput(selectedInputName);
        if (input != null) {
            input.configure();
            inputTableView.getItems().add(input);
            addInputComboBox.getSelectionModel().select(0); // reset ComboBox to "add Input"
        }
    }

    @FXML
    public void addOutputAction(ActionEvent actionEvent) {
        log.debug("addOutputAction: {}", actionEvent);
        String selectedOutputName = addOutputComboBox.getSelectionModel().getSelectedItem();
        Output output = OutputFactory.getOutput(selectedOutputName);
        if (output != null) {
            output.configure();
            ObservableList<Output> outputs = outputTableView.getItems();
            if (!outputs.contains(output)) {
                outputs.add(output);
            }
            addOutputComboBox.getSelectionModel().select(0); // reset ComboBox to "add Output"
        }
    }

    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        ListControllerUtil.deleteKeyListenerTableView(keyEvent);
    }
}
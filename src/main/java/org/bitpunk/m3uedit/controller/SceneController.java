package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
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
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.*;
import org.bitpunk.m3uedit.NamedItem;
import org.bitpunk.m3uedit.dialog.EntryDialogController;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    private final List<String> selectedSorterNames = new ArrayList<>();
    private final List<String> selectedFilterNames = new ArrayList<>();
    @FXML
    public Button saveFileButton;
    @FXML
    public TableColumn<Entry, String> indexColumn;
    @FXML
    public Button addEntryButton;
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
    TableColumn<Entry, String> channelName;
    @FXML
    TableColumn<Entry, String> channelUri;
    @FXML
    private TableView<Entry> entryTableView = new TableView<>();

    private boolean deletePluginDialog(NamedItem deleteItem) {
        Alert dialog = new Alert(Alert.AlertType.WARNING);
        dialog.setTitle("Delete Plugin");
        dialog.setHeaderText("Delete Plugin");
        dialog.setContentText("Sure you want to delete:\n" + deleteItem.getName());

        ButtonType buttonYes = new ButtonType("Yes");
        ButtonType buttonNo = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(buttonYes, buttonNo);
        Optional<ButtonType> selectedOption = dialog.showAndWait();

        return selectedOption.isPresent() && selectedOption.get() == buttonYes;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initEntryTableView();
        initInputTableView();
        initOutputTableView();
        initAvailableInputComboBox();
        initAvailableFilterListView();
        initAvailableSorterListView();
        initAvailableOutputComboBox();
    }

    private void initEntryTableView() {
        // make column channelName editable
        channelName.setCellFactory(TextFieldTableCell.forTableColumn());
        channelName.setOnEditCommit(t -> (t.getTableView().getItems().get(t.getTablePosition().getRow())).setName(t.getNewValue()));

        // make column channelUri editable
        channelUri.setCellFactory(TextFieldTableCell.forTableColumn());
        channelUri.setOnEditCommit(t -> (t.getTableView().getItems().get(t.getTablePosition().getRow())).setName(t.getNewValue()));

        // set RowFactory for Entry-TableView to make rows draggable
        entryTableView.setRowFactory(tv -> {
            TableRow<Entry> row = new TableRow<>();

            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent cc = new ClipboardContent();
                    cc.put(SERIALIZED_MIME_TYPE, index);
                    db.setContent(cc);
                    event.consume();
                }
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE) && (row.getIndex() != (Integer) db.getContent(SERIALIZED_MIME_TYPE))) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    event.consume();

                }
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
                    Entry draggedEntry = entryTableView.getItems().remove(draggedIndex);

                    int dropIndex;

                    if (row.isEmpty()) {
                        dropIndex = entryTableView.getItems().size();
                    } else {
                        dropIndex = row.getIndex();
                        log.debug("Entry dragged to index: {}", dropIndex);
                    }

                    entryTableView.getItems().add(dropIndex, draggedEntry);

                    event.setDropCompleted(true);
                    entryTableView.getSelectionModel().select(dropIndex);
                    event.consume();
                }
            });

            return row;
        });
        entryTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @SuppressWarnings("unchecked")
    private void initInputTableView() {
        TableColumn<Input, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Input, String> sourceColumn = new TableColumn<>("Source");
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("source"));
        inputTableView.getColumns().addAll(nameColumn, sourceColumn);
    }

    @SuppressWarnings("unchecked")
    private void initOutputTableView() {
        TableColumn<Output, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Output, String> sourceColumn = new TableColumn<>("Destination");
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("output"));
        outputTableView.getColumns().addAll(nameColumn, sourceColumn);
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

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Entry, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings.when(cell.emptyProperty()).then("").otherwise(cell.indexProperty().asString()));
            return cell;
        });
    }

    @FXML
    private void saveListToOutputs() {
        ObservableList<Output> outputs = outputTableView.getItems();
        for (Output output : outputs) {
            output.write(entryTableView.getItems());
        }
    }

    @FXML
    private void addNewEntry() {
        EntryDialogController entryDialogController = new EntryDialogController();
        entryDialogController.showAndWait().ifPresent(entry -> {
            log.debug("adding new Entry: {}", entry);
            entryTableView.getItems().add(entry);
        });
    }

    @FXML
    @SuppressWarnings("unchecked")
    private void populateEntryTable() {
        ObservableList<Entry> dataFromInputSource = FXCollections.observableArrayList();
        for (Input currentInput : inputTableView.getItems()) {
            log.debug("reading from: {}", currentInput);
            try {
                dataFromInputSource.addAll(currentInput.read());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        entryTableView.setItems(dataFromInputSource);
        addIndexColumn();

        channelName.setCellValueFactory(new PropertyValueFactory<>("name"));
        channelUri.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.getColumns().setAll(indexColumn, channelName, channelUri);
    }

    @FXML
    private void handleExitAction() {
        System.exit(0);
    }

    @FXML
    @SuppressWarnings("unchecked")
    private void deleteKeyListenerTableView(KeyEvent keyEvent) {
        if (keyEvent.getCode().equals(KeyCode.DELETE)) {
            TableView<NamedItem> sourceTableView = (TableView<NamedItem>) keyEvent.getSource();
            final NamedItem selectedPlugin = sourceTableView.getSelectionModel().getSelectedItem();
            deletePluginFromTableView(selectedPlugin, sourceTableView);
            keyEvent.consume();
        }
    }

    private void deletePluginFromTableView(NamedItem plugin, TableView<NamedItem> tableView) {
        if (deletePluginDialog(plugin)) {
            tableView.getItems().remove(plugin);
        }
    }

    @FXML
    private void applySelectedFilterAction() {
        for (String selectedFilterName : selectedFilterNames) {
            Filter filter = FilterPluginService.getInstance().getFilter(selectedFilterName);
            ObservableList<Entry> filteredEntries = FXCollections.observableList(filter.filter(entryTableView.getItems()));
            entryTableView.setItems(filteredEntries);
        }
    }

    @FXML
    private void applySelectedSorterAction() {
        for (String selectedSorterName : selectedSorterNames) {
            Sorter sorter = SorterPluginService.getInstance().getSorter(selectedSorterName);
            ObservableList<Entry> sortedEntries = FXCollections.observableList(sorter.sort(entryTableView.getItems()));
            entryTableView.setItems(sortedEntries);
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

}
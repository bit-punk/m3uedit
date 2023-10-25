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
import javafx.scene.text.Text;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.PlayList;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.FileInput;
import org.bitpunk.m3uedit.plugins.input.FritzBoxInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.FileOutput;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.SftpOutput;
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
    public TextField targetFileNameTextField;
    @FXML
    public Button saveFileButton;
    @FXML
    public TableColumn<Entry, String> indexColumn;
    @FXML
    public Button addEntryButton;
    @FXML
    public TextField addedChannelNameTextField;
    @FXML
    public TextField addedChannelUriTextField;
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

    private boolean deleteDialog(Entry deleteEntry) {
        Alert dialog = new Alert(Alert.AlertType.WARNING);
        dialog.setTitle("Delete Entry");
        dialog.setHeaderText("Delete Entry");
        dialog.setContentText("Sure you want to delete:\n" + deleteEntry.getChannelName());

        ButtonType buttonYes = new ButtonType("Yes");
        ButtonType buttonNo = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(buttonYes, buttonNo);
        Optional<ButtonType> selectedOption = dialog.showAndWait();

        return selectedOption.isPresent() && selectedOption.get() == buttonYes;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        // make column channelName editable
        channelName.setCellFactory(TextFieldTableCell.forTableColumn());
        channelName.setOnEditCommit(t -> (t.getTableView().getItems().get(t.getTablePosition().getRow())).setChannelName(t.getNewValue()));

        // make column channelUri editable
        channelUri.setCellFactory(TextFieldTableCell.forTableColumn());
        channelUri.setOnEditCommit(t -> (t.getTableView().getItems().get(t.getTablePosition().getRow())).setChannelName(t.getNewValue()));

        // set RowFactory to make rows draggable

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

        //Set the right resize-policy
        entryTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        entryTableView.getColumns().forEach((column) -> {
            //Minimal width = columnheader
            Text text = new Text(column.getText());
            double max = text.getLayoutBounds().getWidth();
            for (int i = 0; i < entryTableView.getItems().size(); i++) {
                //cell must not be empty
                if (column.getCellData(i) != null) {
                    text = new Text(column.getCellData(i).toString());
                    double calcwidth = text.getLayoutBounds().getWidth();
                    //remember new max-width
                    if (calcwidth > max) {
                        max = calcwidth;
                    }
                }
            }
            //set the new max-width with some extra space
            column.setPrefWidth(max + 10.0d);
        });

        entryTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        initInputTableView();
        initOutputTableView();
        initAvailableInputComboBox();
        initAvailableFilterListView();
        initAvailableSorterListView();
        initAvailableOutputComboBox();
    }

    private void initInputTableView() {
        TableColumn<Input, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Input, String> sourceColumn = new TableColumn<>("Source");
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("source"));
        inputTableView.getColumns().addAll(nameColumn, sourceColumn);
    }
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
    private void addEntry() {
        Entry entry = new Entry();
        entry.setChannelName(addedChannelNameTextField.getText());
        entry.setChannelUri(addedChannelUriTextField.getText());
        entryTableView.getItems().add(entry);
        addedChannelNameTextField.clear();
        addedChannelUriTextField.clear();
    }

    @FXML
    private void populateEntryTable() {
        ObservableList<Entry> dataFromInputSource = FXCollections.observableArrayList();
        for (Input currentInput : inputTableView.getItems()) {
            log.debug("reading from: {}", currentInput);
            try {
                dataFromInputSource.addAll(readData(currentInput));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        entryTableView.setItems(dataFromInputSource);
        addIndexColumn();

        channelName.setCellValueFactory(new PropertyValueFactory<>("channelName"));
        channelUri.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.getColumns().setAll(indexColumn, channelName, channelUri);
    }

    private PlayList<Entry> readData(Input input) throws FileNotFoundException {
        Parser parser = new Parser();
        return parser.parse(input.read());
    }

    @FXML
    private void handleExitAction() {
        System.exit(0);
    }

    @FXML
    private void deleteKeyListener(KeyEvent keyEvent) {
        final Entry selectedEntry = entryTableView.getSelectionModel().getSelectedItem();
        if (keyEvent.getCode().equals(KeyCode.DELETE)) {
            deleteEntry(selectedEntry);
            keyEvent.consume();
        }
    }

    private void deleteEntry(Entry selectedEntry) {
        if (deleteDialog(selectedEntry)) {
            entryTableView.getItems().remove(selectedEntry);
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
        // ToDo replace with an InputFactory
        String selectedItem = addInputComboBox.getSelectionModel().getSelectedItem();
        if (selectedItem.equals("File Input")) {
            log.debug("adding new File Input");
            FileInput fileInput = new FileInput();
            fileInput.configure();
            inputTableView.getItems().add(fileInput);
        } else if (selectedItem.equals("FritzBox Input")) {
            log.debug("adding new Fritzbox Input");
            FritzBoxInput fritzBoxInput = new FritzBoxInput();
            inputTableView.getItems().add(fritzBoxInput);
        }
        addInputComboBox.getSelectionModel().select(0);
    }

    @FXML
    public void addOutputAction(ActionEvent actionEvent) {
        log.debug("addOutputAction: {}", actionEvent);
        // ToDo replace with an OutputFactory
        String selectedItem = addOutputComboBox.getSelectionModel().getSelectedItem();
        ObservableList<Output> outputs = outputTableView.getItems();
        if (selectedItem.equals("File Output")) {
            FileOutput fileOutput = new FileOutput();
            fileOutput.configure();

            if (!outputs.contains(fileOutput)) {
                log.debug("adding new File Output");
                outputs.add(fileOutput);
            }
        } else if (selectedItem.equals("SFTP Output")) {
            log.debug("adding new SFTP Output");
            SftpOutput sftpOutput = new SftpOutput();
            outputs.add(sftpOutput);
        }
        addOutputComboBox.getSelectionModel().select(0);

    }
}

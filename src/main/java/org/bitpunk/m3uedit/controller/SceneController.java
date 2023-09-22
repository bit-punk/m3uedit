package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.PlayList;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;

public class SceneController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SceneController.class);
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    @FXML
    public TextField targetFileNameTextField;
    @FXML
    public Button saveFileButton;
    @FXML
    public Button openFileButton;
    @FXML
    public TableColumn<Entry, String> indexColumn;
    @FXML
    public TextField fileNameTextField;
    @FXML
    public Button addEntryButton;
    @FXML
    public TextField addedChannelNameTextField;
    @FXML
    public TextField addedChannelUriTextField;
    @FXML
    public Scene scene;
    @FXML
    public ListView<String> availableFilterListView;
    @FXML
    public ListView<String> selectedFilterListView;
    @FXML
    public Button selectFilterButton;
    @FXML
    public Button unselectFilterButton;
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

        // allow multiple selects in availableFilterListView
        availableFilterListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // allow multiple selects in selectedFilterListView
        selectedFilterListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // make column channelName editable
        channelName.setCellFactory(TextFieldTableCell.forTableColumn());
        channelName.setOnEditCommit(
                t -> (t.getTableView().getItems().get(
                        t.getTablePosition().getRow())
                ).setChannelName(t.getNewValue())
        );

        // make column channelUri editable
        channelUri.setCellFactory(TextFieldTableCell.forTableColumn());
        channelUri.setOnEditCommit(
                t -> (t.getTableView().getItems().get(
                        t.getTablePosition().getRow())
                ).setChannelName(t.getNewValue())
        );

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
        entryTableView.getColumns().forEach((column) ->
        {
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
        initAvailableFilterListView();
    }

    private void initAvailableFilterListView() {
        List<String> filterNames = FilterPluginService.getInstance().getFilterNames();
        log.debug("filterNames: {}", filterNames);
        availableFilterListView.getItems().addAll(filterNames);
    }

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Entry, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings.when(cell.emptyProperty())
                    .then("")
                    .otherwise(cell.indexProperty().asString()));
            return cell;
        });
    }

    @FXML
    private void saveListToFile() {
        savePlayListToFile(targetFileNameTextField.getText());
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
    private void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("M3U-Playlist Files", "*.m3u")
        );
        File selectedFile = fileChooser.showOpenDialog(scene.getWindow());
        log.debug("loading playlist-file: {}", selectedFile);
        String fileName = selectedFile.getAbsolutePath();
        fileNameTextField.setText(fileName);
        populateEntryTable();
    }

    private void savePlayListToFile(String targetFileName) {
        log.debug("saving playlist to file: {}", targetFileName);
        Writer writer = new Writer();
        try {
            writer.write(entryTableView.getItems(), new FileOutputStream(targetFileName));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void populateEntryTable() {
        try {
            ObservableList<Entry> dataFromFile = FXCollections.observableArrayList();
            dataFromFile.addAll(readDataFromFile(fileNameTextField.getText()));
            entryTableView.setItems(dataFromFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        addIndexColumn();

        channelName.setCellValueFactory(new PropertyValueFactory<>("channelName"));
        channelUri.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.getColumns().setAll(indexColumn, channelName, channelUri);
    }

    private PlayList<Entry> readDataFromFile(String fileName) throws FileNotFoundException {
        Parser parser = new Parser();
        InputStream inputStream = new DataInputStream(new FileInputStream(fileName));
        return parser.parse(inputStream);
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
    private void selectFilterAction() {
        ObservableList<String> selectedFilters = availableFilterListView.getSelectionModel().getSelectedItems();
        log.debug("adding selected filters: {}", selectedFilters);
        selectedFilterListView.getItems().addAll(selectedFilters);
        availableFilterListView.getItems().removeAll(selectedFilters);
    }

    @FXML
    private void unselectFilterAction() {
        ObservableList<String> unselectedFilters = selectedFilterListView.getSelectionModel().getSelectedItems();
        log.debug("removing de-selected filters: {}", unselectedFilters);
        availableFilterListView.getItems().addAll(unselectedFilters);
        selectedFilterListView.getItems().removeAll(unselectedFilters);
    }

    @FXML
    private void applySelectedFilterAction() {
        ObservableList<String> selectedFilterNames = selectedFilterListView.getItems();
        for (String selectedFilterName : selectedFilterNames) {
            Filter filter = FilterPluginService.getInstance().getFilter(selectedFilterName);
            ObservableList<Entry> filteredEntries = FXCollections.observableList(filter.filter(entryTableView.getItems()));

            entryTableView.setItems(filteredEntries);
        }

    }
}

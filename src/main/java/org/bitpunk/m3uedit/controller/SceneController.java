package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.PlayList;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.parser.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;

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
    public TableColumn<String, Integer> indexColumn;
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
    TableColumn<Entry, String> channelName;
    @FXML
    TableColumn<Entry, String> channelUri;
    @FXML
    private TableView<Entry> entryTableView = new TableView<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {


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
            Text t = new Text(column.getText());
            double max = t.getLayoutBounds().getWidth();
            for (int i = 0; i < entryTableView.getItems().size(); i++) {
                //cell must not be empty
                if (column.getCellData(i) != null) {
                    t = new Text(column.getCellData(i).toString());
                    double calcwidth = t.getLayoutBounds().getWidth();
                    //remember new max-width
                    if (calcwidth > max) {
                        max = calcwidth;
                    }
                }
            }
            //set the new max-width with some extra space
            column.setPrefWidth(max + 10.0d);
        });

        // saveFileButton
        saveFileButton.setOnAction(e -> savePlayListToFile(targetFileNameTextField.getText()));
    }

    public void addEntry() {
        Entry entry = new Entry();
        entry.setChannelName(addedChannelNameTextField.getText());
        entry.setChannelUri(addedChannelUriTextField.getText());
        entryTableView.getItems().add(entry);
        addedChannelNameTextField.clear();
        addedChannelUriTextField.clear();
    }

    public void openFile() {
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

    public void populateEntryTable() {
        try {
            ObservableList<Entry> dataFromFile = FXCollections.observableArrayList();
            dataFromFile.addAll(readDataFromFile(fileNameTextField.getText()));
            entryTableView.setItems(dataFromFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        indexColumn.setCellFactory(col -> {
            TableCell<String, Integer> indexCell = new TableCell<>();
            ReadOnlyObjectProperty<TableRow<String>> rowProperty = indexCell.tableRowProperty();
            ObjectBinding<String> rowBinding = Bindings.createObjectBinding(() -> {
                TableRow<String> row = rowProperty.get();
                if (row != null) {
                    int rowIndex = row.getIndex();
                    if (rowIndex < row.getTableView().getItems().size()) {
                        return Integer.toString(rowIndex);
                    }
                }
                return null;
            }, rowProperty);
            indexCell.textProperty().bind(rowBinding);
            return indexCell;
        });

        channelName.setCellValueFactory(new PropertyValueFactory<>("channelName"));
        channelUri.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.getColumns().setAll(channelName, channelUri);
    }

    private PlayList<Entry> readDataFromFile(String fileName) throws FileNotFoundException {
        Parser parser = new Parser();
        InputStream inputStream = new DataInputStream(new FileInputStream(fileName));
        return parser.parse(inputStream);
    }

    public void handleExitAction() {
        System.exit(0);
    }
}

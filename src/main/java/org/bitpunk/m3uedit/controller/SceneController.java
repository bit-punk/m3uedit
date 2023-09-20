package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
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
    TableColumn<Entry, String> channelName;
    @FXML
    TableColumn<Entry, String> channelUri;
    private PlayList<Entry> playList = new PlayList<>();
    @FXML
    private TableView<Entry> entryTableView = new TableView<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("M3U-Playlist Files", "*.m3u")
        );

        fileNameTextField.setOnAction(e -> populateEntryTable(fileNameTextField.getText()));

        openFileButton.setOnAction(e -> {
                    Node node = (Node) e.getSource();
                    Stage thisStage = (Stage) node.getScene().getWindow();
                    File selectedFile = fileChooser.showOpenDialog(thisStage);
                    log.debug("loading playlist-file: {}", selectedFile);
                    String fileName = selectedFile.getAbsolutePath();
                    fileNameTextField.setText(fileName);
                    populateEntryTable(fileName);
                }
        );

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

        addEntryButton.setOnAction(e -> {
            Entry entry = new Entry();
            entry.setChannelName(addedChannelNameTextField.getText());
            entry.setChannelUri(addedChannelUriTextField.getText());
            playList.add(entry);
            ObservableList<Entry> dataFromPlaylist = FXCollections.observableArrayList();
            dataFromPlaylist.addAll(playList);
            entryTableView.setItems(dataFromPlaylist);
            addedChannelNameTextField.clear();
            addedChannelUriTextField.clear();
        });

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

        // saveFileButton
        saveFileButton.setOnAction(e -> {
            savePlayListToFile(targetFileNameTextField.getText());
        });
    }

    private void savePlayListToFile(String targetFileName) {
        log.debug("saving playlist to file: {}", targetFileName);
        Writer writer = new Writer();
        try {
            writer.write(playList, new FileOutputStream(targetFileName));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }


    private void populateEntryTable(String fileName) {
        try {
            playList = readDataFromFile(fileName);
            ObservableList<Entry> dataFromFile = FXCollections.observableArrayList();
            dataFromFile.addAll(playList);
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
}

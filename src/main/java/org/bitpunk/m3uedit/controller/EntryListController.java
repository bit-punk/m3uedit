package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import org.bitpunk.m3uedit.dialog.EntryDialogController;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.EntryMapper;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class EntryListController extends VBox implements Initializable, ModelAware {
    private static final Logger log = LoggerFactory.getLogger(EntryListController.class);

    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    @FXML
    public TableColumn<Entry, String> indexColumn;
    @FXML
    TableColumn<Entry, String> channelNameColumn;
    @FXML
    TableColumn<Entry, String> channelUriColumn;
    @FXML
    private TableView<Entry> entryTableView;
    @FXML
    private Button addNewEntryButton;
    private DataModel dataModel;

    public EntryListController() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("fxml/EntryList.fxml"));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initEntryTableView();
        addNewEntryButton.setOnAction(actionEvent -> addNewEntry());
    }

    private void initEntryTableView() {
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

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Entry entry = row.getItem();
                    log.debug("double-clicked on: {}", entry);
                    editEntry(entry);
                    entryTableView.refresh();
                }
            });
            return row;
        });
        entryTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        addIndexColumn();

        channelNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        channelUriColumn.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.getColumns().setAll(indexColumn, channelNameColumn, channelUriColumn);
        entryTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Entry, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings.when(cell.emptyProperty()).then("").otherwise(cell.indexProperty().asString()));
            return cell;
        });
    }

    private void editEntry(Entry entry) {
        EntryDialogController<Entry> entryDialogController = new EntryDialogController<>();
        entryDialogController.setEntry(entry);
        entryDialogController.showAndWait().ifPresent(entryEdited -> {
            log.debug("edited Entry: {}", entryEdited);
            EntryMapper.mapEntry(entryEdited, entry);
        });
    }

    @FXML
    private void addNewEntry() {
        EntryDialogController<Entry> entryDialogController = new EntryDialogController<>();
        entryDialogController.showAndWait().ifPresent(entry -> {
            log.debug("adding new Entry: {}", entry);
            dataModel.getEntries().add(entry);
            entryTableView.refresh();
        });
    }

    @FXML
    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        ListControllerUtil.deleteKeyListenerTableView(keyEvent);
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        entryTableView.setItems(this.dataModel.getEntries());
    }

}
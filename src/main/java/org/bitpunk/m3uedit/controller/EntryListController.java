package org.bitpunk.m3uedit.controller;

import lombok.extern.slf4j.Slf4j;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.bitpunk.m3uedit.util.ListControllerUtil;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
public class EntryListController extends AbstractController  implements Initializable, ModelAware {
    public static final String FXML_ENTRY_LIST = "fxml/EntryList.fxml";
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> indexColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> channelNameColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> tvgIdColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> channelUriColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableView<Entry> entryTableView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Button addNewEntryButton;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Button resetManualChangesButton;
    private DataModel dataModel;

    public EntryListController() {
        super(FXML_ENTRY_LIST);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initEntryTableView();
        addNewEntryButton.setOnAction(actionEvent -> addNewEntry());
        resetManualChangesButton.setOnAction(actionEvent -> resetManualChanges());
    }

    private void initEntryTableView() {
            entryTableView.setRowFactory(tv -> {
                TableRow<Entry> row = new TableRow<>();
                setOnDragDetectedEventHandler(row);
                setOnDragOverEventHandler(row);
                setOnDragEnteredEventHandler(row);
                setOnDragExitedEventHandler(row);
                setOnDragDroppedEventHandler(row);
                setOnMouseClickedEventHandler(row);
                return row;
            });
            entryTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            addIndexColumn();
        ListControllerUtil.addPlayColumn(entryTableView);

        channelNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        // e.g. set by the EPG enricher, players match the channels of the programme guide by it
        tvgIdColumn.setCellValueFactory(new PropertyValueFactory<>("tvgId"));
        channelUriColumn.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    private void setOnMouseClickedEventHandler(TableRow<Entry> row) {
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && (!row.isEmpty())) {
                Entry entry = row.getItem();
                int index = row.getIndex() + 1;
                entry.setIndex(index);
                log.debug("double-clicked on: {}", entry);
                Entry edited = editEntry(entry);
                if (index != edited.getIndex()) {
                    log.debug("index changed from {} to {}", index, edited.getIndex());
                    moveEntryToNewPosition(edited);
                }
                entryTableView.refresh();
            }
        });
    }

    // package-private for tests
    TableView<Entry> entryTableView() {
        return entryTableView;
    }

    // package-private for tests
    void moveEntryToNewPosition(Entry entry) {
        dataModel.moveEntries(List.of(entry), entry.getIndex() - 1);
    }

    private void setOnDragDroppedEventHandler(TableRow<Entry> row) {
        row.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                row.setStyle("");
                Object content = db.getContent(SERIALIZED_MIME_TYPE);

                if (content instanceof IndicesList draggedIndices) {
                    int dropIndex = row.isEmpty() ? entryTableView.getItems().size() : row.getIndex();
                    moveItems(draggedIndices, dropIndex);

                    event.setDropCompleted(true);
                    event.consume();
                }
            }
        });
    }

    // package-private for tests
    void moveItems(List<Integer> draggedIndices, int dropIndex) {
        ObservableList<Entry> items = entryTableView.getItems();
        List<Entry> itemsToMove = draggedIndices.stream()
                .map(items::get)
                .toList();

        // Adjust drop index for removed items
        long itemsBeforeDrop = draggedIndices.stream()
                .filter(i -> i < dropIndex)
                .count();

        int finalDropIndex = Math.max(0, dropIndex - (int) itemsBeforeDrop);
        dataModel.moveEntries(itemsToMove, finalDropIndex);

        entryTableView.getSelectionModel().clearSelection();
        itemsToMove.forEach(entryTableView.getSelectionModel()::select);
    }

    private static void setOnDragOverEventHandler(TableRow<Entry> row) {
        row.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                Object content = db.getContent(SERIALIZED_MIME_TYPE);
                if (content instanceof IndicesList indices && !indices.contains(row.getIndex())) {
                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                        event.consume();
                    }

            }
        });
    }

    private static void setOnDragEnteredEventHandler(TableRow<Entry> row) {
        row.setOnDragEntered(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                Object content = db.getContent(SERIALIZED_MIME_TYPE);
                if (content instanceof IndicesList indices && !indices.contains(row.getIndex())) {
                        row.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 2 0 0 0;");
                        event.consume();
                    }

            }
        });
    }

    private static void setOnDragExitedEventHandler(TableRow<Entry> row) {
        row.setOnDragExited(event -> {
            row.setStyle("");
            event.consume();
        });
    }

    private static void setOnDragDetectedEventHandler(TableRow<Entry> row) {
        row.setOnDragDetected(event -> {
            if (!row.isEmpty()) {
                // Serialize the list of indices instead of a single index
                IndicesList selectedIndices = new IndicesList(row.getTableView().getSelectionModel().getSelectedIndices());
                Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                db.setDragView(row.snapshot(null, null));
                ClipboardContent cc = new ClipboardContent();
                cc.put(SERIALIZED_MIME_TYPE, selectedIndices);
                db.setContent(cc);
                event.consume();
            }
        });
    }

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Entry, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings.when(cell.emptyProperty()).then("").otherwise(cell.indexProperty().add(1).asString()));
            return cell;
        });
    }

    /**
     * @return the entry with the edited values, or the given entry if editing was cancelled
     */
    private Entry editEntry(Entry entry) {
        int entryListSize = dataModel.getSortedEntryList().size();
        EntryDialogController<Entry> entryDialogController = new EntryDialogController<>(entry, entryListSize);
        return entryDialogController.showAndWait()
                .map(entryEdited -> {
                    log.debug("edited Entry: {}", entryEdited);
                    return dataModel.editEntry(entry, entryEdited);
                })
                .orElse(entry);
    }

    @FXML
    private void addNewEntry() {
        EntryDialogController<Entry> entryDialogController = new EntryDialogController<>();
        entryDialogController.showAndWait().ifPresent(entry -> {
            log.debug("adding new Entry: {}", entry);
            dataModel.addEntry(entry);
            entryTableView.refresh();
        });
    }

    private void resetManualChanges() {
        if (DialogUtil.canManualChangesBeReset(dataModel.getManualChanges().size())) {
            dataModel.resetManualChanges();
        }
    }

    @FXML
    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        if (keyEvent.getCode() != KeyCode.DELETE) {
            return;
        }
        keyEvent.consume();
        List<Entry> selected = List.copyOf(entryTableView.getSelectionModel().getSelectedItems());
        if (!selected.isEmpty() && ListControllerUtil.confirmDeletion(selected)) {
            dataModel.removeEntries(selected);
            entryTableView.getSelectionModel().clearSelection();
        }
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        entryTableView.setItems(this.dataModel.getSortedEntryList());
        resetManualChangesButton.disableProperty().bind(Bindings.isEmpty(this.dataModel.getManualChanges()));
    }

    private static class IndicesList extends ArrayList<Integer> {
        IndicesList(java.util.Collection<Integer> c) {
            super(c);
        }
    }

    }
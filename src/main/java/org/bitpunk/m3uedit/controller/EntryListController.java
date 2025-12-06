package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.EntryMapper;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class EntryListController extends AbstractController  implements Initializable, ModelAware {
    public static final String FXML_ENTRY_LIST = "fxml/EntryList.fxml";
    private static final Logger log = LoggerFactory.getLogger(EntryListController.class);
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> indexColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> channelNameColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> channelUriColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableView<Entry> entryTableView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Button addNewEntryButton;
    private DataModel dataModel;

    public EntryListController() {
        super(FXML_ENTRY_LIST);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initEntryTableView();
        addNewEntryButton.setOnAction(actionEvent -> addNewEntry());
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

        channelNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        channelUriColumn.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    private void setOnMouseClickedEventHandler(TableRow<Entry> row) {
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && (!row.isEmpty())) {
                Entry entry = row.getItem();
                log.debug("double-clicked on: {}", entry);
                editEntry(entry);
                entryTableView.refresh();
            }
        });
    }

    private void setOnDragDroppedEventHandler(TableRow<Entry> row) {
        row.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                row.setStyle("");
                Object content = db.getContent(SERIALIZED_MIME_TYPE);

                if (content instanceof ArrayList<?>) {
                    ArrayList<Integer> draggedIndices = (ArrayList<Integer>) content;
                    List<Entry> itemsToMove = new ArrayList<>();
                    for (Integer index : draggedIndices) {
                        itemsToMove.add(entryTableView.getItems().get(index));
                    }

                    int dropIndex = row.isEmpty() ? entryTableView.getItems().size() : row.getIndex();

                    entryTableView.getItems().removeAll(itemsToMove);

                    // Adjust drop index for removed items
                    long itemsBeforeDrop = draggedIndices.stream().filter(i -> i < dropIndex).count();
                    int finalDropIndex = Math.max(0, dropIndex - (int) itemsBeforeDrop);
                    if (finalDropIndex > entryTableView.getItems().size()) {
                        finalDropIndex = entryTableView.getItems().size();
                    }

                    entryTableView.getItems().addAll(finalDropIndex, itemsToMove);

                    entryTableView.getSelectionModel().clearSelection();
                    for (Entry item : itemsToMove) {
                        entryTableView.getSelectionModel().select(item);
                    }
                    
                    event.setDropCompleted(true);
                    event.consume();
                }
            }
        });
    }

    private static void setOnDragOverEventHandler(TableRow<Entry> row) {
        row.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                Object content = db.getContent(SERIALIZED_MIME_TYPE);
                if (content instanceof ArrayList<?>) {
                    ArrayList<Integer> indices = (ArrayList<Integer>) content;
                    if (!indices.contains(row.getIndex())) {
                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                        event.consume();
                    }
                }
            }
        });
    }

    private static void setOnDragEnteredEventHandler(TableRow<Entry> row) {
        row.setOnDragEntered(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                Object content = db.getContent(SERIALIZED_MIME_TYPE);
                if (content instanceof ArrayList<?>) {
                    ArrayList<Integer> indices = (ArrayList<Integer>) content;
                    if (!indices.contains(row.getIndex())) {
                        row.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 2 0 0 0;");
                        event.consume();
                    }
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
                // Serialize list of indices instead of single index
                ArrayList<Integer> selectedIndices = new ArrayList<>(row.getTableView().getSelectionModel().getSelectedIndices());
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
            dataModel.getSortedEntryList().add(entry);
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
        entryTableView.setItems(this.dataModel.getSortedEntryList());
    }

}
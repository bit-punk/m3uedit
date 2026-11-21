package org.bitpunk.m3uedit.controller;

import lombok.extern.slf4j.Slf4j;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider;
import org.bitpunk.m3uedit.util.ChannelLogos;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.bitpunk.m3uedit.util.ListControllerUtil;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;

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
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Button epgButton;
    @FXML
    private TextField searchField;
    @FXML
    private Button clearSearchButton;
    @FXML
    private Label searchResultLabel;
    /**
     * The entries of the playlist shown: those matching the search, all without search. Created by
     * {@link #initModel}, the playlist itself is never changed by the search.
     */
    private FilteredList<Entry> shownEntries;
    private EpgWindow epgWindow;
    /**
     * Updates an open programme guide window once the playlist has not changed for a moment: a re-computation of
     * the pipeline changes it several times, and reading the programme guides takes a while.
     */
    private final PauseTransition epgRefresh = new PauseTransition(Duration.millis(500));
    private DataModel dataModel;

    public EntryListController() {
        super(FXML_ENTRY_LIST);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initEntryTableView();
        addNewEntryButton.setOnAction(actionEvent -> addNewEntry());
        resetManualChangesButton.setOnAction(actionEvent -> resetManualChanges());
        epgButton.setOnAction(actionEvent -> showEpg());
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
        channelNameColumn.setCellFactory(column -> new ChannelNameCell());
        // e.g. set by the EPG enricher, players match the channels of the programme guide by it
        tvgIdColumn.setCellValueFactory(new PropertyValueFactory<>("tvgId"));
        channelUriColumn.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
        entryTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    private void setOnMouseClickedEventHandler(TableRow<Entry> row) {
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && (!row.isEmpty())) {
                Entry entry = row.getItem();
                int index = playlistPosition(row.getIndex());
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

    // package-private for tests: the playlist, the table only shows the entries matching the search
    ObservableList<Entry> playlist() {
        return dataModel.getSortedEntryList();
    }

    // package-private for tests
    TextField searchField() {
        return searchField;
    }

    // package-private for tests
    Label searchResultLabel() {
        return searchResultLabel;
    }

    /**
     * @param shownIndex the index of an entry in the table
     * @return its position in the playlist (starting with 1), which differs from the row while searching
     */
    int playlistPosition(int shownIndex) {
        return shownEntries.getSourceIndex(shownIndex) + 1;
    }

    /**
     * @return true while the search hides entries
     */
    boolean isSearching() {
        return searchField.getText() != null && !searchField.getText().isBlank();
    }

    private void initSearch() {
        searchField.textProperty().addListener((observable, oldText, text) -> applySearch());
        shownEntries.addListener((ListChangeListener<Entry>) change -> updateSearchResult());
        dataModel.getSortedEntryList().addListener((ListChangeListener<Entry>) change -> updateSearchResult());
        clearSearchButton.setOnAction(event -> {
            searchField.clear();
            searchField.requestFocus();
        });
        clearSearchButton.visibleProperty().bind(searchField.textProperty().isNotEmpty());
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                event.consume();
            }
        });
        // Ctrl+F moves to the search of the project shown, not of the other tabs
        sceneProperty().addListener((observable, oldScene, scene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, focusSearchFilter);
            }
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, focusSearchFilter);
            }
        });
        applySearch();
    }

    private final javafx.event.EventHandler<KeyEvent> focusSearchFilter = event -> {
        if (FOCUS_SEARCH.match(event) && isShown()) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
        }
    };

    private static final KeyCombination FOCUS_SEARCH = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);

    /**
     * @return true if this playlist is shown, i.e. its project tab is selected
     */
    private boolean isShown() {
        for (javafx.scene.Node node = this; node != null; node = node.getParent()) {
            if (!node.isVisible()) {
                return false;
            }
        }
        return getScene() != null;
    }

    private void applySearch() {
        String text = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);
        shownEntries.setPredicate(text.isEmpty() ? null : entry -> matches(entry, text));
        updateSearchResult();
    }

    /**
     * @param text the search text in lower case
     */
    static boolean matches(Entry entry, String text) {
        return Stream.of(entry.getName(), entry.getGroupTitle(), entry.getTvgId(), entry.getChannelUri())
                .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(text));
    }

    private void updateSearchResult() {
        searchResultLabel.setText(isSearching()
                ? "%d of %d".formatted(shownEntries.size(), dataModel.getSortedEntryList().size()) : "");
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

    private void setOnDragDetectedEventHandler(TableRow<Entry> row) {
        row.setOnDragDetected(event -> {
            // the position among the entries hidden by the search would be unclear
            if (isSearching()) {
                return;
            }
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
            // the position in the playlist, also while the search hides entries
            cell.textProperty().bind(Bindings.createStringBinding(
                    () -> cell.isEmpty() || cell.getIndex() < 0 || cell.getIndex() >= shownEntries.size() ? ""
                            : Integer.toString(playlistPosition(cell.getIndex())),
                    cell.emptyProperty(), cell.indexProperty(), shownEntries));
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
            deleteSelected();
        }
    }

    /**
     * Removes the selected entries from the playlist, also while the search hides other entries.
     */
    void deleteSelected() {
        List<Entry> selected = List.copyOf(entryTableView.getSelectionModel().getSelectedItems());
        if (!selected.isEmpty()) {
            dataModel.removeEntries(selected);
            entryTableView.getSelectionModel().clearSelection();
        }
    }

    /**
     * @return the enabled enrichers providing programmes, e.g. the EPG enricher
     */
    List<ProgrammeGuideProvider> programmeGuideProviders() {
        return dataModel.getEnrichers().stream()
                .filter(enricher -> enricher.getEnabled().get())
                .filter(ProgrammeGuideProvider.class::isInstance)
                .map(ProgrammeGuideProvider.class::cast)
                .toList();
    }

    private void updateEpgButton() {
        boolean available = !programmeGuideProviders().isEmpty();
        epgButton.setDisable(!available);
        epgButton.setTooltip(new Tooltip(available ? "Show the programmes of the channels of the playlist"
                : "Enable the EPG Enricher to see the programmes of the channels"));
    }

    /**
     * Opens a window with the programmes of the playlist, read in the background.
     */
    void showEpg() {
        showEpg(programmeGuideProviders());
    }

    /**
     * Opens a window with the programmes of the playlist; only one is open at a time: while it is, it is brought
     * to the front instead.
     */
    void showEpg(List<ProgrammeGuideProvider> providers) {
        if (epgWindow != null && epgWindow.isShowing()) {
            epgWindow.setIconified(false);
            epgWindow.toFront();
            return;
        }
        if (providers.isEmpty()) {
            return;
        }
        EpgWindow window = createEpgWindow(providers);
        // the next click opens a new window with the playlist as it is then
        window.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            if (epgWindow == window) {
                epgWindow = null;
            }
        });
        epgWindow = window;
        window.show();
        window.load();
    }

    /**
     * @return the open programme guide window, null if none
     */
    EpgWindow epgWindow() {
        return epgWindow;
    }

    EpgWindow createEpgWindow(List<ProgrammeGuideProvider> providers) {
        return new EpgWindow(getScene() == null ? null : getScene().getWindow(), dataModel.getName(),
                dataModel.getSortedEntryList(), providers);
    }

    Button epgButton() {
        return epgButton;
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        shownEntries = new FilteredList<>(this.dataModel.getSortedEntryList());
        entryTableView.setItems(shownEntries);
        initSearch();
        resetManualChangesButton.disableProperty().bind(Bindings.isEmpty(this.dataModel.getManualChanges()));
        // enabling an enricher re-computes the pipeline
        this.dataModel.pipelineResultProperty().addListener((observable, oldResult, newResult) -> {
            updateEpgButton();
            scheduleEpgRefresh();
        });
        this.dataModel.getEnrichers().addListener((ListChangeListener<Enricher>) change -> updateEpgButton());
        // changes of the user, e.g. entries moved or removed
        this.dataModel.getSortedEntryList().addListener((ListChangeListener<Entry>) change -> scheduleEpgRefresh());
        epgRefresh.setOnFinished(event -> refreshEpgWindow());
        updateEpgButton();
    }

    private void scheduleEpgRefresh() {
        if (epgWindow != null && epgWindow.isShowing()) {
            epgRefresh.playFromStart();
        }
    }

    /**
     * Shows the current playlist and the programmes of the enabled enrichers in the open programme guide window.
     */
    void refreshEpgWindow() {
        if (epgWindow != null && epgWindow.isShowing()) {
            epgWindow.refresh(dataModel.getSortedEntryList(), programmeGuideProviders());
        }
    }

    private static class IndicesList extends ArrayList<Integer> {
        IndicesList(java.util.Collection<Integer> c) {
            super(c);
        }
    }

    
    /**
     * Shows the name of the channel after its logo (tvg-logo). Without a logo the space stays empty, so the names
     * are aligned.
     */
    static class ChannelNameCell extends TableCell<Entry, String> {
        static final double LOGO_WIDTH = 32;
        static final double LOGO_HEIGHT = 20;

        private final ImageView logo = new ImageView();
        private final StackPane logoBox = new StackPane(logo);

        ChannelNameCell() {
            logo.setFitWidth(LOGO_WIDTH);
            logo.setFitHeight(LOGO_HEIGHT);
            logo.setPreserveRatio(true);
            logoBox.setMinSize(LOGO_WIDTH, LOGO_HEIGHT);
            logoBox.setPrefSize(LOGO_WIDTH, LOGO_HEIGHT);
            logoBox.setMaxSize(LOGO_WIDTH, LOGO_HEIGHT);
            setGraphicTextGap(6);
        }

        @Override
        protected void updateItem(String name, boolean empty) {
            super.updateItem(name, empty);
            Entry entry = getTableRow() != null ? getTableRow().getItem() : null;
            if (empty || entry == null) {
                setText(null);
                setGraphic(null);
                logo.setImage(null);
                return;
            }
            setText(name);
            logo.setImage(ChannelLogos.get(entry.getTvgLogo(), LOGO_WIDTH, LOGO_HEIGHT));
            setGraphic(logoBox);
        }

        ImageView logo() {
            return logo;
        }
    }
}
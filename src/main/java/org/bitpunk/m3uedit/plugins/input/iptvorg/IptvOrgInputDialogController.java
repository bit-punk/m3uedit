package org.bitpunk.m3uedit.plugins.input.iptvorg;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import lombok.Getter;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

public class IptvOrgInputDialogController extends AbstractDialogController<List<IptvOrgPlaylist>> implements Initializable {
    public static final String FXML_PLUGIN_INPUT_IPTV_ORG = "plugin/input/iptvorg/IptvOrgInputDialog.fxml";
    public static final String IPTV_ORG_DIALOG_TITLE = "Select iptv-org playlist(s) to read entries from";
    private static final Logger log = LoggerFactory.getLogger(IptvOrgInputDialogController.class);
    private final IptvOrgApiClient apiClient;
    private final ObservableList<IptvOrgPlaylist> availablePlaylists = FXCollections.observableArrayList();
    private final FilteredList<IptvOrgPlaylist> filteredPlaylists = new FilteredList<>(availablePlaylists);
    @FXML
    public ButtonType okButtonType;
    @Getter
    private BooleanBinding nothingSelected;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private ComboBox<PlaylistGrouping> groupingComboBox;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField filterTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private ListView<IptvOrgPlaylist> availablePlaylistListView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Button addPlaylistButton;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private ListView<IptvOrgPlaylist> selectedPlaylistListView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Label statusLabel;

    public IptvOrgInputDialogController(IptvOrgApiClient apiClient, List<IptvOrgPlaylist> preselectedPlaylists) {
        super(FXML_PLUGIN_INPUT_IPTV_ORG, IPTV_ORG_DIALOG_TITLE);
        this.apiClient = apiClient;
        setResultConverter(getPlaylistResultConverter());
        if (preselectedPlaylists != null) {
            selectedPlaylistListView.getItems().addAll(preselectedPlaylists);
        }
        initAvailablePlaylistView();
        initSelectedPlaylistView();
        initGroupingComboBox();
        nothingSelected = Bindings.isEmpty(selectedPlaylistListView.getItems());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // the controls are initialized in the constructor, as the api-client is not yet available when FXML is loaded
    }

    private void initGroupingComboBox() {
        groupingComboBox.getItems().setAll(PlaylistGrouping.values());
        groupingComboBox.valueProperty().addListener((obs, oldGrouping, newGrouping) -> loadPlaylists(newGrouping));
        groupingComboBox.setValue(PlaylistGrouping.COUNTRY);
    }

    private void initAvailablePlaylistView() {
        availablePlaylistListView.setItems(filteredPlaylists);
        availablePlaylistListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        availablePlaylistListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(IptvOrgPlaylist playlist, boolean empty) {
                super.updateItem(playlist, empty);
                setText(empty || playlist == null ? null : "%s (%s)".formatted(playlist.getName(), playlist.getId()));
            }
        });
        availablePlaylistListView.setOnMouseClicked(this::addPlaylistsOnDoubleClick);
        filterTextField.textProperty().addListener((obs, oldText, newText) -> filteredPlaylists.setPredicate(playlist -> matchesFilter(playlist, newText)));
        addPlaylistButton.setOnAction(actionEvent -> addSelectedPlaylists());
        addPlaylistButton.disableProperty().bind(Bindings.isEmpty(availablePlaylistListView.getSelectionModel().getSelectedItems()));
    }

    private void initSelectedPlaylistView() {
        selectedPlaylistListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        selectedPlaylistListView.setOnKeyPressed(this::deleteKeyListener);
    }

    static boolean matchesFilter(IptvOrgPlaylist playlist, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String lowerCaseFilter = filter.trim().toLowerCase(Locale.ROOT);
        return playlist.getName().toLowerCase(Locale.ROOT).contains(lowerCaseFilter)
                || playlist.getId().toLowerCase(Locale.ROOT).contains(lowerCaseFilter);
    }

    private void loadPlaylists(PlaylistGrouping grouping) {
        availablePlaylists.clear();
        if (grouping == null) {
            return;
        }
        statusLabel.setText("Loading %s playlists...".formatted(grouping.getLabel().toLowerCase(Locale.ROOT)));
        Task<List<IptvOrgPlaylist>> loadTask = new Task<>() {
            @Override
            protected List<IptvOrgPlaylist> call() throws Exception {
                return apiClient.getPlaylists(grouping);
            }
        };
        loadTask.setOnSucceeded(event -> {
            // ignore results of a grouping that is no longer selected
            if (grouping == groupingComboBox.getValue()) {
                availablePlaylists.setAll(loadTask.getValue());
                statusLabel.setText("%d playlists available".formatted(availablePlaylists.size()));
            }
        });
        loadTask.setOnFailed(event -> {
            log.error("Unable to load {} playlists from iptv-org", grouping, loadTask.getException());
            if (grouping == groupingComboBox.getValue()) {
                statusLabel.setText("Unable to load playlists: " + loadTask.getException().getMessage());
            }
        });
        Thread loadThread = new Thread(loadTask, "iptv-org-playlist-loader");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void addPlaylistsOnDoubleClick(MouseEvent mouseEvent) {
        if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2) {
            addSelectedPlaylists();
        }
    }

    void addSelectedPlaylists() {
        ObservableList<IptvOrgPlaylist> selectedItems = selectedPlaylistListView.getItems();
        for (IptvOrgPlaylist playlist : availablePlaylistListView.getSelectionModel().getSelectedItems()) {
            if (!selectedItems.contains(playlist)) {
                log.debug("adding playlist {}", playlist);
                selectedItems.add(playlist);
            }
        }
    }

    private void deleteKeyListener(KeyEvent keyEvent) {
        if (keyEvent.getCode().equals(KeyCode.DELETE)) {
            List<IptvOrgPlaylist> playlistsToRemove = new ArrayList<>(selectedPlaylistListView.getSelectionModel().getSelectedItems());
            selectedPlaylistListView.getItems().removeAll(playlistsToRemove);
            keyEvent.consume();
        }
    }

    private Callback<ButtonType, List<IptvOrgPlaylist>> getPlaylistResultConverter() {
        return buttonType -> {
            List<IptvOrgPlaylist> playlists = null;
            if (Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed");
                playlists = new ArrayList<>(selectedPlaylistListView.getItems());
            }
            log.debug("returning result: {}", playlists);
            return playlists;
        };
    }
}

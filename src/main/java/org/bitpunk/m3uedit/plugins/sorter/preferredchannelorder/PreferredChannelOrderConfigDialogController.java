package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.util.Callback;
import javafx.util.StringConverter;
import lombok.Getter;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.parser.ParsingException;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PreferredChannelOrderConfigDialogController extends AbstractDialogController<PreferredChannelOrderConfig> {
    public static final String FXML_PLUGIN_SORTER_CONFIG = "plugin/sorter/preferredchannelorder/PreferredChannelOrderConfigDialog.fxml";
    public static final String DIALOG_TITLE = "Configure Preferred Channel Order";
    private static final Logger log = LoggerFactory.getLogger(PreferredChannelOrderConfigDialogController.class);

    @FXML
    @Getter
    private ComboBox<ChannelProfile> profileComboBox;

    @FXML
    @Getter
    private Button addProfileButton;

    @FXML
    @Getter
    private Button createFromPlaylistButton;

    @FXML
    @Getter
    private Button renameProfileButton;

    @FXML
    @Getter
    private Button deleteProfileButton;

    @FXML
    @Getter
    private CheckBox defaultProfileCheckBox;

    @FXML
    @Getter
    private ListView<String> channelsListView;

    @FXML
    @Getter
    private Button moveUpButton;

    @FXML
    @Getter
    private Button moveDownButton;

    @FXML
    @Getter
    private Button removeChannelButton;

    @FXML
    @Getter
    private TextField newChannelTextField;

    @FXML
    @Getter
    private Button addChannelButton;

    @FXML
    @Getter
    private ButtonType okButtonType;

    @Getter
    private PreferredChannelOrderSorter sorter;

    @Getter
    private PreferredChannelOrderConfig workingConfig;

    private ChannelProfile currentlySelectedProfile;

    public PreferredChannelOrderConfigDialogController() {
        this(new PreferredChannelOrderSorter());
    }

    public PreferredChannelOrderConfigDialogController(PreferredChannelOrderSorter sorter) {
        super(FXML_PLUGIN_SORTER_CONFIG, DIALOG_TITLE);
        this.sorter = sorter;
        setResultConverter(createResultConverter());
        initData(sorter);
    }

    private Callback<ButtonType, PreferredChannelOrderConfig> createResultConverter() {
        return buttonType -> {
            if (Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed in PreferredChannelOrderConfigDialog");
                saveWorkingState();
                applyAndPersistConfig();
                return workingConfig;
            }
            return null;
        };
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupProfileComboBox();
        setupChannelsListView();
        setupChannelOrderButtons();
        setupProfileCrudButtons();
        setupAddChannelControls();
    }

    private void setupProfileComboBox() {
        profileComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ChannelProfile profile) {
                return profile != null ? profile.getName() : "";
            }

            @Override
            public ChannelProfile fromString(String string) {
                return null;
            }
        });

        profileComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChannelProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        profileComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ChannelProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        profileComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldProfile, newProfile) -> {
            onProfileSelectionChanged(oldProfile, newProfile);
        });

        defaultProfileCheckBox.selectedProperty().addListener((obs, oldValue, isDefault) -> {
            ChannelProfile current = profileComboBox.getValue();
            if (current != null && Boolean.TRUE.equals(isDefault)) {
                workingConfig.setDefaultProfile(current.getId());
            }
        });
    }

    private void onProfileSelectionChanged(ChannelProfile oldProfile, ChannelProfile newProfile) {
        if (oldProfile != null) {
            oldProfile.setChannels(new ArrayList<>(channelsListView.getItems()));
        }

        currentlySelectedProfile = newProfile;

        if (newProfile != null) {
            List<String> channels = newProfile.getChannels() != null ? newProfile.getChannels() : new ArrayList<>();
            channelsListView.setItems(FXCollections.observableArrayList(new ArrayList<>(channels)));
            defaultProfileCheckBox.setSelected(Objects.equals(newProfile.getId(), workingConfig.getDefaultProfile()));
        } else {
            channelsListView.setItems(FXCollections.observableArrayList());
            defaultProfileCheckBox.setSelected(false);
        }
    }

    private void setupChannelsListView() {
        channelsListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        channelsListView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item);
                    }
                }
            };

            cell.setOnDragDetected(event -> {
                if (cell.isEmpty() || cell.getItem() == null) {
                    return;
                }
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(cell.getIndex()));
                db.setContent(content);
                event.consume();
            });

            cell.setOnDragOver(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            cell.setOnDragEntered(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    cell.setStyle("-fx-border-color: #2196F3; -fx-border-width: 2 0 0 0;");
                }
            });

            cell.setOnDragExited(event -> cell.setStyle(""));

            cell.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    try {
                        int draggedIdx = Integer.parseInt(db.getString());
                        int targetIdx = cell.isEmpty() ? channelsListView.getItems().size() : cell.getIndex();

                        ObservableList<String> items = channelsListView.getItems();
                        if (draggedIdx >= 0 && draggedIdx < items.size()) {
                            String item = items.remove(draggedIdx);
                            if (draggedIdx < targetIdx) {
                                targetIdx--;
                            }
                            if (targetIdx < 0) {
                                targetIdx = 0;
                            }
                            if (targetIdx > items.size()) {
                                targetIdx = items.size();
                            }
                            items.add(targetIdx, item);
                            channelsListView.getSelectionModel().select(targetIdx);
                            success = true;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            cell.setOnDragDone(DragEvent::consume);

            return cell;
        });

        channelsListView.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        channelsListView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                try {
                    int draggedIdx = Integer.parseInt(db.getString());
                    ObservableList<String> items = channelsListView.getItems();
                    if (draggedIdx >= 0 && draggedIdx < items.size()) {
                        String item = items.remove(draggedIdx);
                        items.add(item);
                        channelsListView.getSelectionModel().select(item);
                        success = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        channelsListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                removeSelectedChannel();
                event.consume();
            }
        });
    }

    private void setupChannelOrderButtons() {
        moveUpButton.setTooltip(new Tooltip("Move Up"));
        moveDownButton.setTooltip(new Tooltip("Move Down"));
        removeChannelButton.setTooltip(new Tooltip("Remove selected channel"));

        moveUpButton.setOnAction(e -> moveUp());
        moveDownButton.setOnAction(e -> moveDown());
        removeChannelButton.setOnAction(e -> removeSelectedChannel());

        moveUpButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> channelsListView.getSelectionModel().getSelectedIndex() <= 0,
                channelsListView.getSelectionModel().selectedIndexProperty(),
                channelsListView.itemsProperty()
        ));

        moveDownButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    int idx = channelsListView.getSelectionModel().getSelectedIndex();
                    return idx < 0 || idx >= channelsListView.getItems().size() - 1;
                },
                channelsListView.getSelectionModel().selectedIndexProperty(),
                channelsListView.itemsProperty()
        ));

        removeChannelButton.disableProperty().bind(
                channelsListView.getSelectionModel().selectedIndexProperty().lessThan(0)
        );
    }

    private void setupProfileCrudButtons() {
        addProfileButton.setTooltip(new Tooltip("Create a new profile"));
        createFromPlaylistButton.setTooltip(new Tooltip("Create a new profile from the channel order of an existing playlist"));
        renameProfileButton.setTooltip(new Tooltip("Rename selected profile"));
        deleteProfileButton.setTooltip(new Tooltip("Delete selected profile"));

        addProfileButton.setOnAction(e -> handleAddProfile());
        createFromPlaylistButton.setOnAction(e -> handleCreateProfileFromPlaylist());
        renameProfileButton.setOnAction(e -> handleRenameProfile());
        deleteProfileButton.setOnAction(e -> handleDeleteProfile());

        renameProfileButton.disableProperty().bind(
                profileComboBox.getSelectionModel().selectedItemProperty().isNull()
        );

        deleteProfileButton.disableProperty().bind(
                profileComboBox.getSelectionModel().selectedItemProperty().isNull()
                        .or(Bindings.size(profileComboBox.getItems()).lessThanOrEqualTo(1))
        );
    }

    private void setupAddChannelControls() {
        addChannelButton.setOnAction(e -> addChannel());
        newChannelTextField.setOnAction(e -> addChannel());

        addChannelButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> newChannelTextField.getText() == null || newChannelTextField.getText().trim().isEmpty(),
                newChannelTextField.textProperty()
        ));
    }

    public void initData(PreferredChannelOrderSorter sorter) {
        this.sorter = sorter;
        PreferredChannelOrderConfig originalConfig = sorter != null ? sorter.getConfig() : null;
        this.workingConfig = copyConfig(originalConfig);

        if (this.workingConfig.getProfiles() == null || this.workingConfig.getProfiles().isEmpty()) {
            ChannelProfile defaultProfile = ChannelProfile.builder()
                    .id("standard")
                    .name("Standard")
                    .channels(new ArrayList<>())
                    .build();
            this.workingConfig.setProfiles(new ArrayList<>(List.of(defaultProfile)));
            this.workingConfig.setDefaultProfile(defaultProfile.getId());
        }

        profileComboBox.getItems().setAll(this.workingConfig.getProfiles());

        String targetProfileId = sorter != null ? sorter.getSelectedProfileId() : null;
        ChannelProfile profileToSelect = findProfileById(targetProfileId)
                .or(() -> findProfileById(workingConfig.getDefaultProfile()))
                .orElse(profileComboBox.getItems().getFirst());

        profileComboBox.getSelectionModel().select(profileToSelect);
    }

    private Optional<ChannelProfile> findProfileById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return profileComboBox.getItems().stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst();
    }

    public void moveUp() {
        int index = channelsListView.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            ObservableList<String> items = channelsListView.getItems();
            String item = items.remove(index);
            items.add(index - 1, item);
            channelsListView.getSelectionModel().select(index - 1);
            channelsListView.scrollTo(index - 1);
        }
    }

    public void moveDown() {
        int index = channelsListView.getSelectionModel().getSelectedIndex();
        ObservableList<String> items = channelsListView.getItems();
        if (index >= 0 && index < items.size() - 1) {
            String item = items.remove(index);
            items.add(index + 1, item);
            channelsListView.getSelectionModel().select(index + 1);
            channelsListView.scrollTo(index + 1);
        }
    }

    public void addChannel() {
        String text = newChannelTextField.getText();
        if (text != null && !text.trim().isEmpty()) {
            addChannel(text.trim());
            newChannelTextField.clear();
        }
    }

    public void addChannel(String channelName) {
        if (channelName != null && !channelName.trim().isEmpty()) {
            String trimmed = channelName.trim();
            channelsListView.getItems().add(trimmed);
            channelsListView.getSelectionModel().select(trimmed);
            channelsListView.scrollTo(trimmed);
        }
    }

    public void removeSelectedChannel() {
        int index = channelsListView.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            channelsListView.getItems().remove(index);
            if (!channelsListView.getItems().isEmpty()) {
                int newIndex = Math.min(index, channelsListView.getItems().size() - 1);
                channelsListView.getSelectionModel().select(newIndex);
            }
        }
    }

    public void handleAddProfile() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Profile");
        dialog.setHeaderText("Create a new channel profile");
        dialog.setContentText("Profile name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                addNewProfile(trimmed);
            }
        });
    }

    public ChannelProfile addNewProfile(String profileName) {
        return addNewProfile(profileName, List.of());
    }

    public ChannelProfile addNewProfile(String profileName, List<String> channels) {
        saveWorkingState();
        String id = generateProfileId(profileName);
        ChannelProfile newProfile = ChannelProfile.builder()
                .id(id)
                .name(profileName)
                .channels(new ArrayList<>(channels))
                .build();
        profileComboBox.getItems().add(newProfile);
        profileComboBox.getSelectionModel().select(newProfile);
        return newProfile;
    }

    public void handleCreateProfileFromPlaylist() {
        Path playlist = DialogUtil.getOpenFilePath("M3U Playlist", "*.m3u", "*.m3u8");
        if (playlist == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(getPlaylistBaseName(playlist));
        dialog.setTitle("New Profile from Playlist");
        dialog.setHeaderText("Create a new channel profile from '" + playlist.getFileName() + "'");
        dialog.setContentText("Profile name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            try {
                addNewProfileFromPlaylist(trimmed, playlist);
            } catch (IOException | ParsingException e) {
                log.warn("Cannot create profile from playlist {}", playlist, e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("New Profile from Playlist");
                alert.setHeaderText("Cannot read playlist");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        });
    }

    public ChannelProfile addNewProfileFromPlaylist(String profileName, Path playlist) throws IOException {
        try (InputStream stream = Files.newInputStream(playlist)) {
            List<String> channels = extractChannelNames(new Parser().parse(stream));
            log.debug("Creating profile '{}' with {} channels from playlist {}", profileName, channels.size(), playlist);
            return addNewProfile(profileName, channels);
        }
    }

    /**
     * Extracts the channel names of the given entries in their playlist order.
     * Blank names are skipped and duplicate names are only kept at their first position.
     */
    static List<String> extractChannelNames(List<Entry> entries) {
        Set<String> channels = new LinkedHashSet<>();
        for (Entry entry : entries) {
            String name = entry.getName();
            if (name != null && !name.isBlank()) {
                channels.add(name.trim());
            }
        }
        return new ArrayList<>(channels);
    }

    private String getPlaylistBaseName(Path playlist) {
        String fileName = playlist.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public void handleRenameProfile() {
        ChannelProfile selected = profileComboBox.getValue();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.getName());
        dialog.setTitle("Rename Profile");
        dialog.setHeaderText("Rename channel profile");
        dialog.setContentText("Profile name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                renameSelectedProfile(trimmed);
            }
        });
    }

    public void renameSelectedProfile(String newName) {
        ChannelProfile selected = profileComboBox.getValue();
        if (selected != null && newName != null && !newName.trim().isEmpty()) {
            selected.setName(newName.trim());
            int index = profileComboBox.getSelectionModel().getSelectedIndex();
            profileComboBox.getItems().set(index, selected);
            profileComboBox.getSelectionModel().select(index);
        }
    }

    public void handleDeleteProfile() {
        ChannelProfile selected = profileComboBox.getValue();
        if (selected == null) {
            return;
        }

        if (profileComboBox.getItems().size() <= 1) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Delete Profile");
            alert.setHeaderText("Cannot delete profile");
            alert.setContentText("At least one profile must exist.");
            alert.showAndWait();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Profile");
        alert.setHeaderText("Delete Profile");
        alert.setContentText("Are you sure you want to delete profile '" + selected.getName() + "'?");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteSelectedProfile();
        }
    }

    public void deleteSelectedProfile() {
        if (profileComboBox.getItems().size() > 1) {
            int index = profileComboBox.getSelectionModel().getSelectedIndex();
            ChannelProfile removed = profileComboBox.getItems().remove(index);
            if (removed != null && Objects.equals(removed.getId(), workingConfig.getDefaultProfile())) {
                workingConfig.setDefaultProfile(profileComboBox.getItems().getFirst().getId());
            }
            int newIndex = Math.min(index, profileComboBox.getItems().size() - 1);
            profileComboBox.getSelectionModel().select(newIndex);
        }
    }

    public void saveWorkingState() {
        ChannelProfile current = profileComboBox.getValue();
        if (current != null) {
            current.setChannels(new ArrayList<>(channelsListView.getItems()));
        }
        workingConfig.setProfiles(new ArrayList<>(profileComboBox.getItems()));
    }

    public void applyAndPersistConfig() {
        if (sorter != null) {
            ChannelProfile selectedProfile = profileComboBox.getValue();
            if (selectedProfile != null) {
                sorter.setSelectedProfileId(selectedProfile.getId());
            }
            sorter.setConfig(workingConfig);
            if (sorter.getConfigManager() != null) {
                sorter.getConfigManager().saveConfig(workingConfig);
            }
        }
    }

    private String generateProfileId(String name) {
        String baseId = name.toLowerCase().replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (baseId.isEmpty()) {
            baseId = "profile";
        }
        String id = baseId;
        int counter = 1;
        while (isProfileIdTaken(id)) {
            id = baseId + "_" + counter++;
        }
        return id;
    }

    private boolean isProfileIdTaken(String id) {
        return profileComboBox.getItems().stream().anyMatch(p -> Objects.equals(p.getId(), id));
    }

    private PreferredChannelOrderConfig copyConfig(PreferredChannelOrderConfig original) {
        if (original == null) {
            return PreferredChannelOrderConfig.builder().build();
        }
        List<ChannelProfile> copiedProfiles = new ArrayList<>();
        if (original.getProfiles() != null) {
            for (ChannelProfile p : original.getProfiles()) {
                copiedProfiles.add(ChannelProfile.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .channels(p.getChannels() != null ? new ArrayList<>(p.getChannels()) : new ArrayList<>())
                        .build());
            }
        }
        return PreferredChannelOrderConfig.builder()
                .defaultProfile(original.getDefaultProfile())
                .profiles(copiedProfiles)
                .build();
    }
}

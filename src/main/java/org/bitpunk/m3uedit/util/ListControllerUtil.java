package org.bitpunk.m3uedit.util;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.shape.SVGPath;
import org.bitpunk.m3uedit.NamedItem;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.player.ExternalPlayer;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

public class ListControllerUtil {
    private static final Logger log = LoggerFactory.getLogger(ListControllerUtil.class);
    private static final double ICON_COLUMN_WIDTH = 32;
    private static final String PENCIL_ICON_RESOURCE = "/icons/pencil.svg";
    private static final String PENCIL_ICON = SvgUtil.loadSvgPath(PENCIL_ICON_RESOURCE);
    private static final String PLAY_ICON_RESOURCE = "/icons/play.svg";
    private static final String PLAY_ICON = SvgUtil.loadSvgPath(PLAY_ICON_RESOURCE);
    private static final ExternalPlayer EXTERNAL_PLAYER = new ExternalPlayer();

    private ListControllerUtil() {}

    public static <T> void setOnRowDoubleClick(TableView<T> tableView, Consumer<T> action) {
        tableView.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    T item = row.getItem();
                    if (item != null && action != null) {
                        action.accept(item);
                    }
                }
            });
            return row;
        });
    }

    public static <T extends Plugin> void configureOnRowDoubleClick(TableView<T> tableView) {
        configureOnRowDoubleClick(tableView, null);
    }

    public static <T extends Plugin> void configureOnRowDoubleClick(TableView<T> tableView, Runnable onAfterConfigured) {
        setOnRowDoubleClick(tableView, item -> configurePlugin(tableView, item, onAfterConfigured));
    }

    public static <T extends Plugin> void addConfigureColumn(TableView<T> tableView) {
        addConfigureColumn(tableView, null);
    }

    public static <T extends Plugin> void addConfigureColumn(TableView<T> tableView, Runnable onAfterConfigured) {
        TableColumn<T, Void> configureColumn = createIconColumn();
        configureColumn.setCellFactory(column -> new TableCell<>() {
            private final Button configureButton = createIconButton(PENCIL_ICON, "Configure");

            {
                configureButton.setOnAction(event -> {
                    T item = getTableRow() != null ? getTableRow().getItem() : null;
                    if (item != null) {
                        configurePlugin(getTableView(), item, onAfterConfigured);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                T plugin = getTableRow() != null ? getTableRow().getItem() : null;
                setGraphic(!empty && plugin != null && plugin.isConfigurable() ? configureButton : null);
            }
        });
        tableView.getColumns().add(configureColumn);
    }

    /**
     * Adds a column with a play button to each row, which opens the entry's stream in the external player.
     */
    public static void addPlayColumn(TableView<Entry> tableView) {
        TableColumn<Entry, Void> playColumn = createIconColumn();
        playColumn.setCellFactory(column -> new TableCell<>() {
            private final Button playButton = createIconButton(PLAY_ICON, "Play in external player");

            {
                playButton.setOnAction(event -> {
                    Entry entry = getTableRow() != null ? getTableRow().getItem() : null;
                    if (entry != null) {
                        playEntry(entry);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                Entry entry = getTableRow() != null ? getTableRow().getItem() : null;
                setGraphic(!empty && entry != null && entry.getChannelUri() != null ? playButton : null);
            }
        });
        tableView.getColumns().add(1, playColumn);
    }

    private static void playEntry(Entry entry) {
        try {
            EXTERNAL_PLAYER.play(entry);
        } catch (IOException | IllegalArgumentException e) {
            log.error("failed to start external player for: {}", entry, e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("External Player");
            alert.setHeaderText("Could not start the external player");
            alert.setContentText("%s%n%nMake sure VLC is installed or configure the player in:%n%s".formatted(
                    e.getMessage(), ExternalPlayer.getConfigPath()));
            alert.setResizable(true);
            alert.showAndWait();
        }
    }

    private static <T> TableColumn<T, Void> createIconColumn() {
        TableColumn<T, Void> column = new TableColumn<>();
        column.setSortable(false);
        column.setReorderable(false);
        column.setResizable(false);
        column.setPrefWidth(ICON_COLUMN_WIDTH);
        column.setMinWidth(ICON_COLUMN_WIDTH);
        column.setMaxWidth(ICON_COLUMN_WIDTH);
        return column;
    }

    private static Button createIconButton(String svgPath, String tooltip) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        Button button = new Button();
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setStyle("-fx-background-color: transparent; -fx-padding: 2; -fx-cursor: hand;");
        return button;
    }

    private static <T extends Plugin> void configurePlugin(TableView<T> tableView, T item, Runnable onAfterConfigured) {
        item.configure();
        tableView.refresh();
        if (onAfterConfigured != null) {
            onAfterConfigured.run();
        }
    }

    @FXML
    @SuppressWarnings("unchecked")
    public static void deleteKeyListenerTableView(KeyEvent keyEvent) {
        if (keyEvent.getCode().equals(KeyCode.DELETE)) {
            TableView<NamedItem> targetTableView = (TableView<NamedItem>) keyEvent.getTarget();
            final ObservableList<NamedItem> selectedPlugins = targetTableView.getSelectionModel().getSelectedItems();
            deleteNamedItemsFromTableView(selectedPlugins, targetTableView);
            keyEvent.consume();
        }
    }

    private static void deleteNamedItemsFromTableView(ObservableList<NamedItem> deletePlugins, TableView<NamedItem> tableView) {
        if (deleteNamedItemsDialog(deletePlugins)) {
            tableView.getItems().removeAll(deletePlugins);
        }
    }

    private static boolean deleteNamedItemsDialog(ObservableList<NamedItem> deleteItems) {
        Alert dialog = new Alert(Alert.AlertType.WARNING);
        dialog.setTitle("Delete Item");
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(377, 270);
        dialog.setHeaderText("Delete Item");
        String deleteConfirmMessage = "Sure you want to delete %d items:%n%n%s".formatted(deleteItems.size(), EntryListNameFormatter.getNamesSummary(deleteItems));
        dialog.setContentText(deleteConfirmMessage);

        ButtonType buttonYes = new ButtonType("Yes");
        ButtonType buttonNo = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(buttonYes, buttonNo);
        Optional<ButtonType> selectedOption = dialog.showAndWait();

        return selectedOption.isPresent() && selectedOption.get() == buttonYes;
    }
}
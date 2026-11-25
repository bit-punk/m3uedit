package org.bitpunk.m3uedit.util;

import lombok.extern.slf4j.Slf4j;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.shape.SVGPath;
import org.bitpunk.m3uedit.api.NamedItem;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.player.ExternalPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class ListControllerUtil {
    private static final double ICON_COLUMN_WIDTH = 32;
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

    /**
     * Adds a column with a play button to each row, which opens the entry's stream in the external player.
     */
    public static void addPlayColumn(TableView<Entry> tableView) {
        TableColumn<Entry, Void> playColumn = createIconColumn();
        playColumn.setCellFactory(column -> new TableCell<>() {
            private final Button playButton = createIconButton(PLAY_ICON, Messages.get("player.play.tooltip"));

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

    /**
     * Opens the entry's stream in the external player; an error is shown to the user.
     */
    public static void playEntry(Entry entry) {
        try {
            EXTERNAL_PLAYER.play(entry);
        } catch (IOException | IllegalArgumentException e) {
            log.error("failed to start external player for: {}", entry, e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(Messages.get("player.error.title"));
            alert.setHeaderText(Messages.get("player.error.header"));
            alert.setContentText(Messages.get("player.error.content", String.valueOf(e.getMessage()),
                    ExternalPlayer.getConfigPath()));
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

    /**
     * Asks whether the given items shall be deleted.
     *
     * @return true if the user confirmed
     */
    public static boolean confirmDeletion(List<? extends NamedItem> items) {
        return deleteNamedItemsDialog(FXCollections.observableArrayList(items));
    }

    private static boolean deleteNamedItemsDialog(ObservableList<NamedItem> deleteItems) {
        Alert dialog = new Alert(Alert.AlertType.WARNING);
        dialog.setTitle(Messages.get("dialog.delete.title"));
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(377, 270);
        dialog.setHeaderText(Messages.get("dialog.delete.title"));
        String deleteConfirmMessage = Messages.get("dialog.delete.content", deleteItems.size(),
                EntryListNameFormatter.getNamesSummary(deleteItems));
        dialog.setContentText(deleteConfirmMessage);

        ButtonType buttonYes = new ButtonType(Messages.get("common.yes"));
        ButtonType buttonNo = new ButtonType(Messages.get("common.no"), ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(buttonYes, buttonNo);
        Optional<ButtonType> selectedOption = dialog.showAndWait();

        return selectedOption.isPresent() && selectedOption.get() == buttonYes;
    }
}
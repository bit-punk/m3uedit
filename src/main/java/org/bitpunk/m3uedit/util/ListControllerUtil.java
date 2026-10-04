package org.bitpunk.m3uedit.util;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.shape.SVGPath;
import org.bitpunk.m3uedit.NamedItem;
import org.bitpunk.m3uedit.plugins.Plugin;

import java.util.Optional;
import java.util.function.Consumer;

public class ListControllerUtil {
    private static final double CONFIGURE_COLUMN_WIDTH = 32;
    private static final String PENCIL_ICON_RESOURCE = "/icons/pencil.svg";
    private static final String PENCIL_ICON = SvgUtil.loadSvgPath(PENCIL_ICON_RESOURCE);

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
        TableColumn<T, Void> configureColumn = new TableColumn<>();
        configureColumn.setSortable(false);
        configureColumn.setReorderable(false);
        configureColumn.setResizable(false);
        configureColumn.setPrefWidth(CONFIGURE_COLUMN_WIDTH);
        configureColumn.setMinWidth(CONFIGURE_COLUMN_WIDTH);
        configureColumn.setMaxWidth(CONFIGURE_COLUMN_WIDTH);
        configureColumn.setCellFactory(column -> new TableCell<>() {
            private final Button configureButton = createConfigureButton();

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

    private static Button createConfigureButton() {
        SVGPath pencil = new SVGPath();
        pencil.setContent(PENCIL_ICON);
        Button button = new Button();
        button.setGraphic(pencil);
        button.setTooltip(new Tooltip("Configure"));
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
package org.bitpunk.m3uedit.util;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.bitpunk.m3uedit.NamedItem;

import java.util.Optional;

public class ListControllerUtil {

    private ListControllerUtil() {}

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
        dialog.setHeaderText("Delete Item");
        String deleteConfirmMessage = String.format("Sure you want to delete %d items:\n\n%s", deleteItems.size(), EntryListNameFormatter.getNamesSummary(deleteItems));
        dialog.setContentText(deleteConfirmMessage);

        ButtonType buttonYes = new ButtonType("Yes");
        ButtonType buttonNo = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(buttonYes, buttonNo);
        Optional<ButtonType> selectedOption = dialog.showAndWait();

        return selectedOption.isPresent() && selectedOption.get() == buttonYes;
    }
}
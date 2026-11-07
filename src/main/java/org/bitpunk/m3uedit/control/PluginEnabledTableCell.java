package org.bitpunk.m3uedit.control;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import org.bitpunk.m3uedit.plugins.Plugin;

/**
 * Table cell showing a {@link CheckBox} bound bidirectionally to the {@link Plugin#getEnabled() enabled}-property
 * of the plugin in its row.
 * <p>
 * Cells are reused for different rows, so the binding to the previous plugin is removed before binding the new one.
 *
 * @param <T> the plugin type shown in the table
 */
public class PluginEnabledTableCell<T extends Plugin> extends TableCell<T, Boolean> {
    private final CheckBox checkBox = new CheckBox();
    private BooleanProperty boundProperty;

    @Override
    protected void updateItem(Boolean item, boolean empty) {
        super.updateItem(item, empty);
        unbind();
        int index = getIndex();
        if (empty || getTableView() == null || index < 0 || index >= getTableView().getItems().size()) {
            setGraphic(null);
            return;
        }
        boundProperty = getTableView().getItems().get(index).getEnabled();
        checkBox.selectedProperty().bindBidirectional(boundProperty);
        setGraphic(checkBox);
    }

    private void unbind() {
        if (boundProperty != null) {
            checkBox.selectedProperty().unbindBidirectional(boundProperty);
            boundProperty = null;
        }
    }

    /**
     * @return the checkbox shown by this cell (for tests)
     */
    CheckBox getCheckBox() {
        return checkBox;
    }
}

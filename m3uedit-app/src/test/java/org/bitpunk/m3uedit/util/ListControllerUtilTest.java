package org.bitpunk.m3uedit.util;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ListControllerUtilTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private <T> TableRow<T> createRow(TableView<T> tableView, int index) {
        TableRow<T> row = (TableRow<T>) tableView.getRowFactory().call(tableView);
        try {
            Method updateTableViewMethod = TableRow.class.getDeclaredMethod("updateTableView", TableView.class);
            updateTableViewMethod.setAccessible(true);
            updateTableViewMethod.invoke(row, tableView);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        row.updateIndex(index);
        return row;
    }

    private MouseEvent createDoubleClickEvent() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                MouseButton.PRIMARY,
                2,
                false, false, false, false,
                false, false, false, false,
                false, false, null
        );
    }

    private MouseEvent createSingleClickEvent() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                MouseButton.PRIMARY,
                1,
                false, false, false, false,
                false, false, false, false,
                false, false, null
        );
    }

    @Test
    void testSetOnRowDoubleClickCustomAction() {
        TableView<String> tableView = new TableView<>();
        tableView.getItems().addAll("item1", "item2");

        AtomicReference<String> clickedItem = new AtomicReference<>();
        ListControllerUtil.setOnRowDoubleClick(tableView, clickedItem::set);

        TableRow<String> row = createRow(tableView, 1);
        row.getOnMouseClicked().handle(createDoubleClickEvent());

        assertThat(clickedItem.get()).isEqualTo("item2");
    }

    @Test
    void testSetOnRowDoubleClickSingleClickDoesNotTrigger() {
        TableView<String> tableView = new TableView<>();
        tableView.getItems().addAll("item1", "item2");

        AtomicReference<String> clickedItem = new AtomicReference<>();
        ListControllerUtil.setOnRowDoubleClick(tableView, clickedItem::set);

        TableRow<String> row = createRow(tableView, 0);
        row.getOnMouseClicked().handle(createSingleClickEvent());

        assertThat(clickedItem.get()).isNull();
    }
}

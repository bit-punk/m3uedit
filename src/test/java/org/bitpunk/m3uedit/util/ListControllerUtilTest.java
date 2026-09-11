package org.bitpunk.m3uedit.util;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void testConfigureOnRowDoubleClickWithCallback() {
        TableView<TestPlugin> tableView = new TableView<>();
        TestPlugin plugin = new TestPlugin("Plugin1");
        tableView.getItems().add(plugin);

        AtomicBoolean callbackRun = new AtomicBoolean(false);
        ListControllerUtil.configureOnRowDoubleClick(tableView, () -> callbackRun.set(true));

        TableRow<TestPlugin> row = createRow(tableView, 0);
        row.getOnMouseClicked().handle(createDoubleClickEvent());

        assertThat(plugin.configured).isTrue();
        assertThat(callbackRun.get()).isTrue();
    }

    @Test
    void testConfigureOnRowDoubleClickWithoutCallback() {
        TableView<TestPlugin> tableView = new TableView<>();
        TestPlugin plugin = new TestPlugin("Plugin1");
        tableView.getItems().add(plugin);

        ListControllerUtil.configureOnRowDoubleClick(tableView);

        TableRow<TestPlugin> row = createRow(tableView, 0);
        row.getOnMouseClicked().handle(createDoubleClickEvent());

        assertThat(plugin.configured).isTrue();
    }

    private static class TestPlugin implements Plugin {
        private final String name;
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);
        boolean configured = false;

        TestPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "desc";
        }

        @Override
        public BooleanProperty getEnabled() {
            return enabled;
        }

        @Override
        public void configure() {
            this.configured = true;
        }
    }
}

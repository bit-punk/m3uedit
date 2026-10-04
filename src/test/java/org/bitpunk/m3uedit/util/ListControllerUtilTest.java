package org.bitpunk.m3uedit.util;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.SVGPath;
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

    @SuppressWarnings("unchecked")
    private TableCell<TestPlugin, Void> createConfigureCell(TableView<TestPlugin> tableView, int index) {
        TableColumn<TestPlugin, Void> column = (TableColumn<TestPlugin, Void>) tableView.getColumns().getLast();
        TableCell<TestPlugin, Void> cell = column.getCellFactory().call(column);
        TableRow<TestPlugin> row = new TableRow<>();
        try {
            Method updateTableViewMethod = TableRow.class.getDeclaredMethod("updateTableView", TableView.class);
            updateTableViewMethod.setAccessible(true);
            updateTableViewMethod.invoke(row, tableView);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        row.updateIndex(index);
        cell.updateTableView(tableView);
        cell.updateTableColumn(column);
        cell.updateTableRow(row);
        cell.updateIndex(index);
        return cell;
    }

    @Test
    void testAddConfigureColumnAddsColumn() {
        TableView<TestPlugin> tableView = new TableView<>();

        ListControllerUtil.addConfigureColumn(tableView);

        assertThat(tableView.getColumns()).hasSize(1);
        assertThat(tableView.getColumns().getFirst().isSortable()).isFalse();
    }

    @Test
    void testAddConfigureColumnShowsButtonForConfigurablePlugin() {
        TableView<TestPlugin> tableView = new TableView<>();
        tableView.getItems().add(new TestPlugin("Plugin1", true));
        ListControllerUtil.addConfigureColumn(tableView);

        TableCell<TestPlugin, Void> cell = createConfigureCell(tableView, 0);

        assertThat(cell.getGraphic()).isInstanceOf(Button.class);
        Button button = (Button) cell.getGraphic();
        assertThat(button.getGraphic()).isInstanceOf(SVGPath.class);
        assertThat(((SVGPath) button.getGraphic()).getContent()).isNotEmpty();
    }

    @Test
    void testAddConfigureColumnHidesButtonForNonConfigurablePlugin() {
        TableView<TestPlugin> tableView = new TableView<>();
        tableView.getItems().add(new TestPlugin("Plugin1", false));
        ListControllerUtil.addConfigureColumn(tableView);

        TableCell<TestPlugin, Void> cell = createConfigureCell(tableView, 0);

        assertThat(cell.getGraphic()).isNull();
    }

    @Test
    void testAddConfigureColumnButtonConfiguresPluginAndRunsCallback() {
        TableView<TestPlugin> tableView = new TableView<>();
        TestPlugin plugin = new TestPlugin("Plugin1", true);
        tableView.getItems().add(plugin);
        AtomicBoolean callbackRun = new AtomicBoolean(false);
        ListControllerUtil.addConfigureColumn(tableView, () -> callbackRun.set(true));

        TableCell<TestPlugin, Void> cell = createConfigureCell(tableView, 0);
        ((Button) cell.getGraphic()).fire();

        assertThat(plugin.configured).isTrue();
        assertThat(callbackRun.get()).isTrue();
    }

    private static class TestPlugin implements Plugin {
        private final String name;
        private final boolean configurable;
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);
        boolean configured = false;

        TestPlugin(String name) {
            this(name, true);
        }

        TestPlugin(String name, boolean configurable) {
            this.name = name;
            this.configurable = configurable;
        }

        @Override
        public boolean isConfigurable() {
            return configurable;
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

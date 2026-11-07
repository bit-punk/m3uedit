package org.bitpunk.m3uedit.control;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.sdchannel.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PluginEnabledTableCellTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    void testCellIsBoundToThePluginOfItsRowOnly() throws Exception {
        runOnFxThread(() -> {
            Filter first = new UniqueNameFilter();
            Filter second = new SdChannelFilter();
            TableView<Filter> table = new TableView<>(FXCollections.observableArrayList(first, second));
            TableColumn<Filter, Boolean> column = new TableColumn<>();
            table.getColumns().add(column);
            PluginEnabledTableCell<Filter> cell = new PluginEnabledTableCell<>();
            cell.updateTableView(table);
            cell.updateTableColumn(column);

            cell.updateIndex(0);
            assertThat(cell.getGraphic()).isSameAs(cell.getCheckBox());
            cell.getCheckBox().setSelected(true);
            assertThat(first.getEnabled().get()).isTrue();

            // the cell is reused for the second row
            cell.updateIndex(1);
            assertThat(cell.getCheckBox().isSelected()).isFalse();
            cell.getCheckBox().setSelected(true);
            cell.getCheckBox().setSelected(false);
            assertThat(first.getEnabled().get()).as("no longer bound to the first plugin").isTrue();
            assertThat(second.getEnabled().get()).isFalse();

            second.getEnabled().set(true);
            assertThat(cell.getCheckBox().isSelected()).isTrue();

            // empty rows show no checkbox and are not bound
            cell.updateIndex(5);
            assertThat(cell.getGraphic()).isNull();
            second.getEnabled().set(false);
            assertThat(cell.getCheckBox().isSelected()).isTrue();
        });
    }
}

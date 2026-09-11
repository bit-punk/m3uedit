package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PluginControllerRowClickTest {

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

    @Test
    void testInputControllerRowDoubleClickConfiguresInput() {
        AtomicBoolean configured = new AtomicBoolean(false);
        Input mockInput = new Input() {
            private final BooleanProperty enabled = new SimpleBooleanProperty(true);

            @Override
            public List<Entry> read() throws FileNotFoundException {
                return List.of();
            }

            @Override
            public String getSource() {
                return "test-source";
            }

            @Override
            public void configure() {
                configured.set(true);
            }

            @Override
            public String getName() {
                return "MockInput";
            }

            @Override
            public String getDescription() {
                return "Mock Input Description";
            }

            @Override
            public BooleanProperty getEnabled() {
                return enabled;
            }
        };

        DataModel dataModel = DataModelFactory.createDataModel("test");
        InputController inputController = new InputController();
        inputController.initModel(dataModel);
        dataModel.getInputs().add(mockInput);
        inputController.inputTableView.getSelectionModel().select(mockInput);

        TableRow<Input> row = createRow(inputController.inputTableView, 0);

        row.getOnMouseClicked().handle(createDoubleClickEvent());

        assertThat(configured.get()).isTrue();
    }

    @Test
    void testOutputControllerRowDoubleClickConfiguresOutput() {
        AtomicBoolean configured = new AtomicBoolean(false);
        Output mockOutput = new Output() {
            private final BooleanProperty enabled = new SimpleBooleanProperty(true);

            @Override
            public void write(List<Entry> playlist) { /* method is empty, during tests we do not creat output */ }

            @Override
            public void configure() {
                configured.set(true);
            }

            @Override
            public URI getOutput() {
                return null;
            }

            @Override
            public String getName() {
                return "MockOutput";
            }

            @Override
            public String getDescription() {
                return "Mock Output Description";
            }

            @Override
            public BooleanProperty getEnabled() {
                return enabled;
            }
        };

        DataModel dataModel = DataModelFactory.createDataModel("test");
        OutputController outputController = new OutputController();
        outputController.initModel(dataModel);
        dataModel.getOutputs().add(mockOutput);
        outputController.outputTableView.getSelectionModel().select(mockOutput);

        TableRow<Output> row = createRow(outputController.outputTableView, 0);

        row.getOnMouseClicked().handle(createDoubleClickEvent());

        assertThat(configured.get()).isTrue();
    }

    @Test
    void testSorterControllerRowDoubleClickConfiguresSorter() {
        AtomicBoolean configured = new AtomicBoolean(false);
        Sorter mockSorter = new Sorter() {
            private final BooleanProperty enabled = new SimpleBooleanProperty(false);

            @Override
            public List<Entry> sort(List<Entry> entries) {
                return entries;
            }

            @Override
            public String getDescription() {
                return "Mock Sorter Description";
            }

            @Override
            public BooleanProperty getEnabled() {
                return enabled;
            }

            @Override
            public String getName() {
                return "MockSorter";
            }

            @Override
            public void configure() {
                configured.set(true);
            }
        };

        DataModel dataModel = DataModelFactory.createDataModel("test");
        dataModel.getSorters().add(mockSorter);
        SorterController sorterController = new SorterController();
        sorterController.initModel(dataModel);
        sorterController.availableSortersTableView.getSelectionModel().select(mockSorter);

        int index = sorterController.availableSortersTableView.getItems().indexOf(mockSorter);
        TableRow<Sorter> row = createRow(sorterController.availableSortersTableView, index);

        row.getOnMouseClicked().handle(createDoubleClickEvent());

        assertThat(configured.get()).isTrue();
    }

    @Test
    void testFilterControllerRowDoubleClickConfiguresFilter() {
        AtomicBoolean configured = new AtomicBoolean(false);
        Filter mockFilter = new Filter() {
            private final BooleanProperty enabled = new SimpleBooleanProperty(false);

            @Override
            public List<Entry> filter(List<Entry> list) {
                return list;
            }

            @Override
            public String getDescription() {
                return "Mock Filter Description";
            }

            @Override
            public BooleanProperty getEnabled() {
                return enabled;
            }

            @Override
            public String getName() {
                return "MockFilter";
            }

            @Override
            public void configure() {
                configured.set(true);
            }
        };

        DataModel dataModel = DataModelFactory.createDataModel("test");
        dataModel.getFilters().add(mockFilter);
        FilterController filterController = new FilterController();
        filterController.initModel(dataModel);
        filterController.availableFiltersTableView.getSelectionModel().select(mockFilter);

        int index = filterController.availableFiltersTableView.getItems().indexOf(mockFilter);
        TableRow<Filter> row = createRow(filterController.availableFiltersTableView, index);

        row.getOnMouseClicked().handle(createDoubleClickEvent());

        assertThat(configured.get()).isTrue();
    }
}

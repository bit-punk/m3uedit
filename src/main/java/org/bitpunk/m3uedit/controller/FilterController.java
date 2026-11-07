package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.bitpunk.m3uedit.control.PluginEnabledTableCell;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.util.ListControllerUtil;

public class FilterController extends AbstractController implements ModelAware {
    public static final String FXML_FILTER = "fxml/Filter.fxml";
    @FXML
    public TableView<Filter> availableFiltersTableView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private CheckBox selectAllEntriesCheckBox;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Filter, Boolean> selectedColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Filter, String> indexColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Filter, String> filterNameColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Filter, String> filterDescriptionColumn;
    private DataModel dataModel;

    public FilterController() {
        super(FXML_FILTER);
    }


    private void initAvailableFilterTableView() {
        availableFiltersTableView.getItems().addAll(dataModel.getFilters());
        selectedColumn.setCellFactory(column -> new PluginEnabledTableCell<>());
        // re-compute the entries once per change of a filter, independent of the (reused) table cells
        for (Filter filter : dataModel.getFilters()) {
            filter.getEnabled().addListener((observable, oldValue, newValue) -> dataModel.filterRawEntries());
        }
        selectedColumn.setCellValueFactory(cellData -> cellData.getValue().getEnabled());
        selectAllEntriesCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> changeAllEntries(newValue));
        addIndexColumn();
        filterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        filterDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        Runnable onAfterConfigured = () -> {
            if (dataModel != null) {
                dataModel.filterRawEntries();
            }
        };
        ListControllerUtil.configureOnRowDoubleClick(availableFiltersTableView, onAfterConfigured);
        ListControllerUtil.addConfigureColumn(availableFiltersTableView, onAfterConfigured);
    }

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Filter, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings.when(cell.emptyProperty()).then("").otherwise(cell.indexProperty().add(1).asString()));
            return cell;
        });
    }

    @Override
    public void initModel(DataModel dataModel) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Data model already initialized");
        }
        this.dataModel = dataModel;
        initAvailableFilterTableView();
    }

    private void changeAllEntries(Boolean newValue) {
        for (Filter filter : availableFiltersTableView.getItems()) {
            filter.getEnabled().set(newValue);
        }
    }

}
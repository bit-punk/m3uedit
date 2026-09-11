package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
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

    private TableCell<Filter, Boolean> getFilterTableCell(TableColumn<Filter, Boolean> param) {
        return new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                int index = getIndex();
                if (empty || index < 0 || index >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    final CheckBox checkBox = new CheckBox();
                    Filter filter = getTableView().getItems().get(index);
                    checkBox.selectedProperty().bindBidirectional(filter.getEnabled());     // bind CheckBox with enabled-Property of Filter
                    filter.getEnabled().addListener((observable, oldValue, newValue) -> dataModel.filterRawEntries());
                    setGraphic(checkBox);
                }
            }
        };
    }

    private void initAvailableFilterTableView() {
        availableFiltersTableView.getItems().addAll(dataModel.getFilters());
        selectedColumn.setCellFactory(this::getFilterTableCell);
        selectedColumn.setCellValueFactory(cellData -> cellData.getValue().getEnabled());
        selectAllEntriesCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> changeAllEntries(newValue));
        addIndexColumn();
        filterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        filterDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        ListControllerUtil.configureOnRowDoubleClick(availableFiltersTableView, () -> {
            if (dataModel != null) {
                dataModel.filterRawEntries();
            }
        });
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
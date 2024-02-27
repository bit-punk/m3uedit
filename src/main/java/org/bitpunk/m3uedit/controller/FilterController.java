package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.net.URL;
import java.util.ResourceBundle;

public class FilterController extends AbstractController {
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

    private static TableCell<Filter, Boolean> getFilterTableCell(TableColumn<Filter, Boolean> param) {
        return new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                updateCheckBox(item, empty);
            }

            private void updateCheckBox(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                int index = getIndex();
                if (empty || index < 0 || index >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    final CheckBox checkBox = new CheckBox();
                    Filter filter = getTableView().getItems().get(index);
                    checkBox.selectedProperty().bindBidirectional(filter.getEnabled());
                    setGraphic(checkBox);
                }
            }
        };
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    private void initAvailableFilterTableView() {
        availableFiltersTableView.getItems().addAll(dataModel.getFilters());
        selectedColumn.setCellFactory(FilterController::getFilterTableCell);
        selectedColumn.setCellValueFactory(cellData -> cellData.getValue().getEnabled());
        selectAllEntriesCheckBox.selectedProperty().addListener(this::changeAllEntries);
        addIndexColumn();
        filterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        filterDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    private void initListener() {
        dataModel.getFilters().addListener((ListChangeListener<? super Filter>) listChangeListener -> filterEntries());
    }

    private void filterEntries() {
        dataModel.getFilters().forEach(filter -> filter.filter(dataModel.getEntries()));
    }

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Filter, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings.when(cell.emptyProperty()).then("").otherwise(cell.indexProperty().asString()));
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
        initListener();
    }

    private void changeAllEntries(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        for (Filter filter : availableFiltersTableView.getItems()) {
            filter.getEnabled().set(newValue);
        }
    }

}
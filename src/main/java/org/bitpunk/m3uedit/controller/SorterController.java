package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;

public class SorterController extends AbstractController implements ModelAware {
    public static final String FXML_SORTER = "fxml/Sorter.fxml";

    @FXML
    public TableView<Sorter> availableSortersTableView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private CheckBox selectAllEntriesCheckBox;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Sorter, Boolean> selectedColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Sorter, String> indexColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Sorter, String> sorterNameColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Sorter, String> sorterDescriptionColumn;
    private DataModel dataModel;

    public SorterController() {
        super(FXML_SORTER);
    }

    private TableCell<Sorter, Boolean> createCheckBoxTableCell(TableColumn<Sorter, Boolean> param) {
        return new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                int index = getIndex();
                if (empty || index < 0 || index >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    final CheckBox checkBox = new CheckBox();
                    Sorter sorter = getTableView().getItems().get(index);
                    checkBox.selectedProperty().bindBidirectional(sorter.getEnabled());
                    sorter.getEnabled().addListener((observable, oldValue, newValue) -> dataModel.sortFilteredEntries());
                    setGraphic(checkBox);
                }
            }
        };
    }

    private void initAvailableSorterListView() {
        availableSortersTableView.getItems().addAll(dataModel.getSorters());
        selectedColumn.setCellFactory(this::createCheckBoxTableCell);
        selectAllEntriesCheckBox.selectedProperty().addListener(this::changeAllEntries);
        addIndexColumn();
        sorterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sorterDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    @Override
    public void initModel(DataModel dataModel) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Data model already initialized");
        }
        this.dataModel = dataModel;
        initAvailableSorterListView();
    }

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Sorter, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings
                    .when(cell.emptyProperty()).then("")
                    .otherwise(cell.indexProperty().add(1).asString()));
            return cell;
        });
    }

    private void changeAllEntries(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        for (Sorter sorter : availableSortersTableView.getItems()) {
            sorter.getEnabled().set(newValue);
        }
    }

}
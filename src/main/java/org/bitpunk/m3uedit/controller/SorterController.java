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
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.util.ListControllerUtil;

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


    private void initAvailableSorterListView() {
        availableSortersTableView.getItems().addAll(dataModel.getSorters());
        selectedColumn.setCellFactory(column -> new PluginEnabledTableCell<>());
        // re-compute the entries once per change of a sorter, independent of the (reused) table cells
        for (Sorter sorter : dataModel.getSorters()) {
            sorter.getEnabled().addListener((observable, oldValue, newValue) -> dataModel.sortFilteredEntries());
        }
        selectAllEntriesCheckBox.selectedProperty()
                .addListener((observable, oldValue, newValue) -> changeAllEntries(newValue));
        addIndexColumn();
        sorterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sorterDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        Runnable onAfterConfigured = () -> {
            if (dataModel != null) {
                dataModel.sortFilteredEntries();
            }
        };
        ListControllerUtil.configureOnRowDoubleClick(availableSortersTableView, onAfterConfigured);
        ListControllerUtil.addConfigureColumn(availableSortersTableView, onAfterConfigured);
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

    private void changeAllEntries(Boolean newValue) {
        for (Sorter sorter : availableSortersTableView.getItems()) {
            sorter.getEnabled().set(newValue);
        }
    }

}
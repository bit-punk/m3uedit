package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class FilteredEntriesController extends AbstractController  implements Initializable, ModelAware {
    public static final String FXML_ENTRY_LIST = "fxml/FilteredEntriesList.fxml";
    private static final Logger log = LoggerFactory.getLogger(FilteredEntriesController.class);
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> indexColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> channelNameColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableColumn<Entry, String> channelUriColumn;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TableView<Entry> filteredEntriesTableView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private DataModel dataModel;

    public FilteredEntriesController() {
        super(FXML_ENTRY_LIST);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.debug("initialize");
        initEntryTableView();
    }

    private void initEntryTableView() {
        // set RowFactory for Entry-TableView to make rows draggable
        filteredEntriesTableView.setRowFactory(tv -> new TableRow<>());
        filteredEntriesTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        addIndexColumn();

        channelNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        channelUriColumn.setCellValueFactory(new PropertyValueFactory<>("channelUri"));
    }

    private void addIndexColumn() {
        indexColumn.setCellFactory(col -> {
            TableCell<Entry, String> cell = new TableCell<>();
            cell.textProperty().bind(Bindings.when(cell.emptyProperty()).then("").otherwise(cell.indexProperty().add(1).asString()));
            return cell;
        });
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        filteredEntriesTableView.setItems(this.dataModel.getFilteredEntryList());
    }

}
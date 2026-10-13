package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class RawEntriesController extends AbstractController  implements Initializable, ModelAware {
    public static final String FXML_ENTRY_LIST = "fxml/RawEntriesList.fxml";
    private static final Logger log = LoggerFactory.getLogger(RawEntriesController.class);
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
    private TableView<Entry> rawEntriesTableView;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private DataModel dataModel;

    public RawEntriesController() {
        super(FXML_ENTRY_LIST);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.debug("initialize");
        initEntryTableView();
    }

    private void initEntryTableView() {
        // set RowFactory for Entry-TableView to make rows draggable
        rawEntriesTableView.setRowFactory(tv -> new TableRow<>());
        rawEntriesTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        addIndexColumn();
        ListControllerUtil.addPlayColumn(rawEntriesTableView);

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
        rawEntriesTableView.setItems(this.dataModel.getRawEntryList());
    }

}
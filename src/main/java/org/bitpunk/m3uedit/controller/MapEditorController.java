package org.bitpunk.m3uedit.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import org.bitpunk.m3uedit.model.DataModel;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MapEditorController extends AbstractController {
    public static final String FXML_MAP_EDITOR = "fxml/MapEditor.fxml";

    @FXML
    private TableView<Map.Entry<String, String>> vlcOptionsTableView;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> keyColumn;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> valueColumn;

    public MapEditorController() {
        super(FXML_MAP_EDITOR);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeTableView();
    }

    public void setMap(Map<String, String> map) {
        vlcOptionsTableView.setItems(FXCollections.observableArrayList(map.entrySet()));
    }

    public Map<String, String> getMap() {
        return vlcOptionsTableView.getItems().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void initializeTableView() {
        keyColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getKey()));
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getValue()));

        // make column 'key' editable
        keyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        keyColumn.setOnEditCommit(t -> (t.getTableView().getItems().get(t.getTablePosition().getRow())).setValue(t.getNewValue()));

        // make column 'value' editable
        valueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        valueColumn.setOnEditCommit(t -> (t.getTableView().getItems().get(t.getTablePosition().getRow())).setValue(t.getNewValue()));
    }

    @Override
    public void initModel(DataModel dataModel) {
        // Todo evaluate if AbstractController really should implements ModelAware-interface
    }

}
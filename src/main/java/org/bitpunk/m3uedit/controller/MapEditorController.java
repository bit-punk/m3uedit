package org.bitpunk.m3uedit.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyEvent;
import org.bitpunk.m3uedit.control.ValidatingEditingCell;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class MapEditorController extends AbstractController {
    public static final String FXML_MAP_EDITOR = "fxml/MapEditor.fxml";
    private static final Logger log = LoggerFactory.getLogger(MapEditorController.class);
    @FXML
    private TableView<NamedEntry> vlcOptionsTableView;
    @FXML
    private TableColumn<NamedEntry, String> keyColumn;
    @FXML
    private TableColumn<NamedEntry, String> valueColumn;
    @FXML
    private TextField newKeyTextField;
    @FXML
    private TextField newValueTextField;
    @FXML
    private Button addNewPropertyButton;

    public MapEditorController() {
        super(FXML_MAP_EDITOR);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeTableView();
        initializeNewPropertyButton();
    }

    private void initializeTableView() {
        keyColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getKey()));
        // make column 'key' editable
        keyColumn.setCellFactory(tc -> new ValidatingEditingCell<>(this::isUniqueKey));
        keyColumn.setOnEditCommit(t -> {
            (t.getTableView().getItems().get(t.getTablePosition().getRow())).setKey(t.getNewValue());
            vlcOptionsTableView.refresh();
        });

        // make column 'value' editable
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getValue()));
        valueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        valueColumn.setOnEditCommit(t -> (t.getTableView().getItems().get(t.getTablePosition().getRow())).setValue(t.getNewValue()));

        vlcOptionsTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
        vlcOptionsTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    }
    private void initializeNewPropertyButton() {
        this.addNewPropertyButton.setOnAction(e -> addNewProperty());
    }

    public Map<String, String> getMap() {
        Map<String, String> vlcOptions = new HashMap<>();
        ObservableList<NamedEntry> items = vlcOptionsTableView.getItems();
        log.debug("getMap({})", items);
        items.forEach(e -> vlcOptions.put(e.getKey(), e.getValue()));
        return vlcOptions;
    }

    public void setMap(Map<String, String> map) {
        List<NamedEntry> namedEntryList = map.entrySet().stream().map(e -> new NamedEntry(e.getKey(), e.getValue())).toList();
        vlcOptionsTableView.setItems(FXCollections.observableArrayList(namedEntryList));
    }

    private boolean isUniqueKey(String newKey) {
        return vlcOptionsTableView.getItems().stream().noneMatch(e -> e.getKey().equals(newKey));
    }

    @Override
    public void initModel(DataModel dataModel) {
        // Todo evaluate if AbstractController really should implement ModelAware-interface
    }

    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        ListControllerUtil.deleteKeyListenerTableView(keyEvent);
    }

    @FXML
    private void addNewProperty() {
        String key = newKeyTextField.getText();
        if (isNotEmpty(key) && isUniqueKey(key)) {
            unmarkTextField(newKeyTextField);
            vlcOptionsTableView.getItems().add(new NamedEntry(key, newValueTextField.getText()));
            newKeyTextField.setText(null);
            newValueTextField.setText(null);
        } else {
            log.error("error trying to add a key that already exists!");
            markTextFieldInvalid(newKeyTextField);
        }
    }

    private void markTextFieldInvalid(TextField textField) {
        textField.setStyle(CSS_INVALID_TEXT_FIELD_INPUT);
        textField.setTooltip(new Tooltip("Key already exists"));
    }

    private void unmarkTextField(TextField textField) {
        textField.setStyle(null);
        textField.setTooltip(null);
    }

}
package org.bitpunk.m3uedit.controller;

import lombok.extern.slf4j.Slf4j;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyEvent;
import org.bitpunk.m3uedit.control.ValidatingEditingCell;
import org.bitpunk.m3uedit.util.ListControllerUtil;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Slf4j
public class MapEditorController extends AbstractController implements Initializable {
    public static final String FXML_MAP_EDITOR = "fxml/MapEditor.fxml";
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
        // in the order shown
        Map<String, String> vlcOptions = new LinkedHashMap<>();
        ObservableList<NamedEntry> items = vlcOptionsTableView.getItems();
        log.debug("getMap({})", items);
        items.forEach(e -> vlcOptions.put(e.getKey(), e.getValue()));
        return vlcOptions;
    }

    public void setMap(Map<String, String> map) {
        if (map == null) {
            map = Map.of();
        }
        List<NamedEntry> namedEntryList = map.entrySet().stream().map(e -> new NamedEntry(e.getKey(), e.getValue())).toList();
        vlcOptionsTableView.setItems(FXCollections.observableArrayList(namedEntryList));
    }

    private boolean isUniqueKey(String newKey) {
        return vlcOptionsTableView.getItems().stream().noneMatch(e -> e.getKey().equals(newKey));
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
package org.bitpunk.m3uedit.controller;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputFactory;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class InputController extends VBox implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(InputController.class);

    @FXML
    public ComboBox<String> addInputComboBox;
    @FXML
    public TableView<Input> inputTableView;

    public InputController() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("fxml/Input.fxml"));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @FXML
    private void addInputAction(ActionEvent actionEvent) {
        log.debug("addInputAction: {}", actionEvent);
        String selectedInputName = addInputComboBox.getSelectionModel().getSelectedItem();
        Input input = InputFactory.getInput(selectedInputName);
        if (input != null) {
            input.configure();
            inputTableView.getItems().add(input);
            addInputComboBox.getSelectionModel().select(0); // reset ComboBox to "add Input"
        }
    }

    @SuppressWarnings("unchecked")
    private void initInputTableView() {
        TableColumn<Input, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Input, String> sourceColumn = new TableColumn<>("Source");
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("source"));
        inputTableView.getColumns().addAll(nameColumn, sourceColumn);
        inputTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        inputTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    private void initAvailableInputComboBox() {
        List<String> inputNames = InputPluginService.getInstance().getInputNames();
        log.debug("inputNames: {}", inputNames);
        addInputComboBox.getItems().add("add Input");
        addInputComboBox.getItems().addAll(inputNames);
        addInputComboBox.getSelectionModel().select(0);
        addInputComboBox.setOnAction(this::addInputAction);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initInputTableView();
        initAvailableInputComboBox();
    }

    public ObservableList<Input> getSelectedInputs() {
        return inputTableView.getItems();
    }

    @FXML
    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        ListControllerUtil.deleteKeyListenerTableView(keyEvent);
    }
}
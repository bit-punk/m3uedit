package org.bitpunk.m3uedit.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputFactory;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class InputController extends AbstractController {
    public static final String FXML_INPUT = "fxml/Input.fxml";
    public static final String ADD_INPUT = "add Input";
    private static final Logger log = LoggerFactory.getLogger(InputController.class);
    @FXML
    public ComboBox<String> addInputComboBox;
    @FXML
    public TableView<Input> inputTableView;
    private DataModel dataModel;

    public InputController() {
        super(FXML_INPUT);
    }

    @Override
    public void initModel(DataModel dataModel) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Data model already initialized");
        }
        this.dataModel = dataModel;
        inputTableView.setItems(this.dataModel.getInputs());
    }

    @FXML
    private void addInputAction(ActionEvent actionEvent) {
        log.debug("addInputAction: {}", actionEvent);
        String selectedInputName = addInputComboBox.getSelectionModel().getSelectedItem();
        if (!selectedInputName.equals(ADD_INPUT)) {
            Input input = InputFactory.getInput(selectedInputName);
            if (input != null) {
                input.configure();
                if (input.getSource() != null) {
                    dataModel.getInputs().add(input);
                }
                addInputComboBox.getSelectionModel().select(0); // reset ComboBox to "add Input"
            }
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
        List<String> inputNames = InputPluginService.getInstance().getAllPluginNames();
        log.debug("inputNames: {}", inputNames);
        addInputComboBox.getItems().add(ADD_INPUT);
        addInputComboBox.getItems().addAll(inputNames);
        addInputComboBox.getSelectionModel().select(0);
        addInputComboBox.setOnAction(this::addInputAction);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initInputTableView();
        initAvailableInputComboBox();
    }

    @FXML
    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        ListControllerUtil.deleteKeyListenerTableView(keyEvent);
    }
}
package org.bitpunk.m3uedit.controller;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.OutputFactory;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class OutputController extends AbstractController implements Initializable, ModelAware {
    private static final Logger log = LoggerFactory.getLogger(OutputController.class);
    public static final String FXML_OUTPUT = "fxml/Output.fxml";
    public static final String ADD_OUTPUT = "add Output";

    @FXML
    public ComboBox<String> addOutputComboBox;
    @FXML
    public TableView<Output> outputTableView;
    @FXML
    public Button saveFileButton;

    private DataModel dataModel;

    public OutputController() {
        super(FXML_OUTPUT);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initOutputTableView();
        initAvailableOutputComboBox();
        saveFileButton.setOnAction(actionEvent -> saveListToOutputs());
    }

    @SuppressWarnings("unchecked")
    private void initOutputTableView() {
        TableColumn<Output, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Output, String> sourceColumn = new TableColumn<>("Destination");
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("output"));
        outputTableView.getColumns().addAll(nameColumn, sourceColumn);
        ListControllerUtil.configureOnRowDoubleClick(outputTableView);
        ListControllerUtil.addConfigureColumn(outputTableView);
        outputTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        outputTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    private void initAvailableOutputComboBox() {
        List<String> outputNames = OutputPluginService.getInstance().getAllPluginNames();
        log.debug("outputNames: {}", outputNames);
        addOutputComboBox.getItems().add(ADD_OUTPUT);
        addOutputComboBox.getItems().addAll(outputNames);
        addOutputComboBox.getSelectionModel().select(0);
        addOutputComboBox.setOnAction(this::addOutputAction);
    }
    @FXML
    public void addOutputAction(ActionEvent actionEvent) {
        log.debug("addOutputAction: {}", actionEvent);
        String selectedOutputName = addOutputComboBox.getSelectionModel().getSelectedItem();
        Output output = OutputFactory.getOutput(selectedOutputName);
        if (output != null) {
            output.configure();
            ObservableList<Output> outputs = dataModel.getOutputs();
            if (!outputs.contains(output)) {
                outputs.add(output);
            }
            addOutputComboBox.getSelectionModel().select(0); // reset ComboBox to "add Output"
        }
    }

    @FXML
    private void saveListToOutputs() {
        dataModel.saveEntries();
    }

    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        ListControllerUtil.deleteKeyListenerTableView(keyEvent);
    }

    @Override
    public void initModel(DataModel dataModel) {
        this.dataModel = dataModel;
        outputTableView.setItems(this.dataModel.getOutputs());
    }
}
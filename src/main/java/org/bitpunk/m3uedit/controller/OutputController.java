package org.bitpunk.m3uedit.controller;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.OutputFactory;
import org.bitpunk.m3uedit.util.ListControllerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class OutputController extends VBox implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(OutputController.class);

    @FXML
    public ComboBox<String> addOutputComboBox;
    @FXML
    public TableView<Output> outputTableView;
    @FXML
    public Button saveFileButton;
    @FXML
    @Getter
    @Setter
    public EntryListController entryListController;

    public OutputController() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("fxml/Output.fxml"));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
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
        outputTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        outputTableView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    private void initAvailableOutputComboBox() {
        List<String> outputNames = OutputPluginService.getInstance().getOutputNames();
        log.debug("outputNames: {}", outputNames);
        addOutputComboBox.getItems().add("add Output");
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
            ObservableList<Output> outputs = outputTableView.getItems();
            if (!outputs.contains(output)) {
                outputs.add(output);
            }
            addOutputComboBox.getSelectionModel().select(0); // reset ComboBox to "add Output"
        }
    }

    @FXML
    private void saveListToOutputs() {
        ObservableList<Output> outputs = outputTableView.getItems();
        for (Output output : outputs) {
            output.write(entryListController.getItems());
        }
    }

    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        ListControllerUtil.deleteKeyListenerTableView(keyEvent);
    }
}
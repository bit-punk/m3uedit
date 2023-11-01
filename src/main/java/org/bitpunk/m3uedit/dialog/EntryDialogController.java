package org.bitpunk.m3uedit.dialog;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import org.bitpunk.m3uedit.model.Entry;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class EntryDialogController extends Dialog<Entry> implements Initializable {

    @FXML
    public TextField durationTextField;
    @FXML
    public TextField nameTextField;
    @FXML
    public TextField channelUriTextField;

    public EntryDialogController() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getClassLoader().getResource("dialog/EntryDialog.fxml"));
            loader.setController(this);

            DialogPane dialogPane = loader.load();

            initModality(Modality.APPLICATION_MODAL);

            setResizable(true);
            setTitle("Edit Entry");
            setDialogPane(dialogPane);
            setResultConverter(buttonType -> {
                if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                    return null;
                }

                Entry newEntry = new Entry();
                newEntry.setName(nameTextField.getText());
                newEntry.setDuration(durationTextField.getText());
                newEntry.setChannelUri(channelUriTextField.getText());
                return newEntry;
            });

            setOnShowing(dialogEvent -> Platform.runLater(() -> durationTextField.requestFocus()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}
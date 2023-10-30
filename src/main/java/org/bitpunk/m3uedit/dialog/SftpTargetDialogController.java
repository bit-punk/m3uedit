package org.bitpunk.m3uedit.dialog;

import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class SftpTargetDialogController extends Dialog<URI> implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SftpTargetDialogController.class);
    @FXML
    public ButtonType okButtonType;
    @FXML
    public TextField hostnameTextField;
    @FXML
    private TextField usernameTextField;
    @FXML
    private PasswordField passwordField;
    private BooleanBinding inputsFull;

    public SftpTargetDialogController() {
        log.debug("initialize()");
//        initializeButtons();

        initModality(Modality.APPLICATION_MODAL);

        setResizable(true);
        setTitle("Enter Connection");
        setResultConverter(buttonType -> {
            if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed");
                return null;
            }
            URI uri;
            try {
                // ToDo create URI after validation from data in input-fields
                uri = new URI("sftp", "test:test", "127.0.0.1", 443, "/home/test/", null, null);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            return uri;
        });
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.debug("initialize()");
        initializeButtons();
    }

    private void initializeButtons() {
        log.debug("hostnameTextField: {}", hostnameTextField);
        log.debug("usernameTextField: {}", usernameTextField);
        log.debug("passwordField: {}", passwordField);
        inputsFull = new BooleanBinding() {
            {
                bind(hostnameTextField.textProperty(), usernameTextField.textProperty(), passwordField.textProperty());
            }

            @Override
            protected boolean computeValue() {
                return !(hostnameTextField.getText().trim().isEmpty() ||
                        usernameTextField.getText().trim().isEmpty() ||
                        passwordField.getText().trim().isEmpty());
            }
        };
    }

    public BooleanBinding inputsFullBinding() {
        return inputsFull;
    }

    public final boolean getInputsFull() {
        return inputsFull.get();
    }

    public BooleanBinding inputsFullProperty() {
        return inputsFull;
    }

    public void initialize() {
        inputsFull = new BooleanBinding() {
            {
                bind(hostnameTextField.textProperty(), usernameTextField.textProperty(), passwordField.textProperty());
            }

            @Override
            protected boolean computeValue() {
                return !(hostnameTextField.getText().trim().isEmpty() || usernameTextField.getText().trim().isEmpty() || passwordField.getText().trim().isEmpty());
            }
        };
    }
}
package org.bitpunk.m3uedit.dialog;

import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.util.Callback;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class SftpTargetDialogController extends Dialog<URI> implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SftpTargetDialogController.class);
    public static final String SFTP_SCHEMA = "sftp";
    @FXML
    public ButtonType okButtonType;
    @FXML
    private TextField hostnameTextField;
    @FXML
    private TextField hostPortTextField;
    @FXML
    private TextField hostPathTextField;
    @FXML
    private TextField usernameTextField;
    @FXML
    private PasswordField passwordField;
    @Getter
    private BooleanBinding inputsFull;

    public SftpTargetDialogController() {
        log.debug("initialize()");
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle("Enter SFTP-Connection");
        setResultConverter(getURIResultConverter());
    }

    private Callback<ButtonType, URI> getURIResultConverter() {
        return buttonType -> {
            URI uri = null;
            if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed");
                try {
                    uri = getUri(SFTP_SCHEMA, usernameTextField.getText(),
                            passwordField.getText(),
                            hostnameTextField.getText(),
                            Integer.parseInt(hostPortTextField.getText()),
                            hostPathTextField.getText());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
            log.debug("returning result: {}", uri);
            return uri;
        };
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.debug("initialize()");
        initializeBinding();
    }

    private void initializeBinding() {
        inputsFull = new BooleanBinding() {
            {
                bind(hostnameTextField.textProperty(),
                        hostPortTextField.textProperty(),
                        hostPathTextField.textProperty(),
                        usernameTextField.textProperty(),
                        passwordField.textProperty());
            }

            @Override
            protected boolean computeValue() {
                return (isValidHostnameInput() &&
                        isValidHostPortInput() &&
                        isValidHostPathInput() &&
                        isValidUsernameInput() &&
                        isValidPasswordInput() &&
                        isValidUriInput());
            }
        };
    }

    private boolean isValidPasswordInput() {
        return !passwordField.getText().trim().isEmpty();
    }

    private boolean isValidUsernameInput() {
        return !usernameTextField.getText().trim().isEmpty();
    }

    private boolean isValidHostnameInput() {
        return !hostnameTextField.getText().trim().isEmpty();
    }

    private boolean isValidHostPortInput() {
        return hostPortTextField.getText().trim().matches("^\\d*$");
    }

    private boolean isValidHostPathInput() {
        String pathString = hostPathTextField.getText().trim();
        return pathString.startsWith("/");
    }

    private boolean isValidUriInput() {
        try {
            getUri(SFTP_SCHEMA,
                    usernameTextField.getText(),
                    passwordField.getText(),
                    hostnameTextField.getText(),
                    Integer.parseInt(hostPortTextField.getText()),
                    hostPathTextField.getText());
        } catch (URISyntaxException uriSyntaxException) {
            return false;
        }
        return true;
    }

    private URI getUri(String schema, String userName, String password, String hostName, int port, String path) throws URISyntaxException {
        return new URI(schema, userName + ":" + password, hostName, port, path, null, null);
    }

}
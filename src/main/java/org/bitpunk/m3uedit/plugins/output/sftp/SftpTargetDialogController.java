package org.bitpunk.m3uedit.plugins.output.sftp;

import jakarta.validation.ConstraintViolation;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.util.Callback;
import lombok.Getter;
import org.bitpunk.m3uedit.dialog.AbstractDialog;
import org.bitpunk.m3uedit.plugins.output.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.util.PojoValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

public class SftpTargetDialogController extends AbstractDialog<SftpConnection> implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SftpTargetDialogController.class);
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
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle("Enter SFTP-Connection");
        setResultConverter(getURIResultConverter());
    }

    private Callback<ButtonType, SftpConnection> getURIResultConverter() {
        return buttonType -> {
            SftpConnection sftpConnection = null;
            if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed");
                sftpConnection = createSftpConnectionFromInput();
            }
            log.debug("returning result: {}", sftpConnection);
            return sftpConnection;
        };
    }

    private SftpConnection createSftpConnectionFromInput() {
        SftpConnection sftpConnection = new SftpConnection();
        sftpConnection.setHostName(hostnameTextField.getText());
        sftpConnection.setPort(Integer.parseInt(hostPortTextField.getText()));
        sftpConnection.setPath(hostPathTextField.getText());
        sftpConnection.setUserName(usernameTextField.getText());
        sftpConnection.setPassword(passwordField.getText());
        return sftpConnection;
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
                return isValidBean();
            }
        };
    }

    private boolean isValidBean() {
        PojoValidator<SftpConnection> validator = new PojoValidator<>();
        SftpConnection sftpConnectionFromInput = createSftpConnectionFromInput();
        Set<ConstraintViolation<SftpConnection>> errors = validator.validate(sftpConnectionFromInput);
        displayValidationErrors(errors);
        log.debug("Validation-errors found: {}", errors);
        return errors.isEmpty() && sftpConnectionFromInput.isValidUri();
    }

    private void displayValidationErrors(Set<ConstraintViolation<SftpConnection>> errors) {
        resetTextFields();
        for (ConstraintViolation<SftpConnection> error : errors) {
            String errorMessage = error.getMessage();
            String fieldName = error.getPropertyPath().toString();
            log.debug("Field: {} Message: {}", fieldName, errorMessage);
            switch (fieldName) {
                case "hostName" -> markFieldInvalid(hostnameTextField, errorMessage);
                case "port" -> markFieldInvalid(hostPortTextField, errorMessage);
                case "path" -> markFieldInvalid(hostPathTextField, errorMessage);
                case "userName" -> markFieldInvalid(usernameTextField, errorMessage);
                case "password" -> markFieldInvalid(passwordField, errorMessage);
            }
        }
    }

    private void resetTextFields() {
        resetTextField(hostnameTextField);
        resetTextField(hostPortTextField);
        resetTextField(hostPathTextField);
        resetTextField(usernameTextField);
        resetTextField(passwordField);
    }

}
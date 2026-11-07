package org.bitpunk.m3uedit.plugins.output.sftp;

import jakarta.validation.ConstraintViolation;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.Callback;
import lombok.Getter;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.bitpunk.m3uedit.plugins.output.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.util.PojoValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

public class SftpTargetDialogController extends AbstractDialogController<SftpConnection> implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SftpTargetDialogController.class);
    public static final String FXML_PLUGIN_OUTPUT_SFTP = "plugin/output/sftp/SftpTargetDialog.fxml";
    public static final String SFTP_DIALOG_TITLE = "Enter SFTP-Connection";
    @FXML
    public ButtonType okButtonType;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField hostnameTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField hostPortTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField hostPathTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField fileNameTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField usernameTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private PasswordField passwordField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Button browseButton;
    @Getter
    private BooleanBinding inputsFull;

    public SftpTargetDialogController() {
        super(FXML_PLUGIN_OUTPUT_SFTP, SFTP_DIALOG_TITLE);
        setResizable(true);
        setResultConverter(getURIResultConverter());
    }

    private Callback<ButtonType, SftpConnection> getURIResultConverter() {
        return buttonType -> {
            SftpConnection sftpConnection = null;
            if (Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed");
                sftpConnection = createSftpConnectionFromInput();
            }
            log.debug("returning result: {}", sftpConnection);
            return sftpConnection;
        };
    }

    /**
     * Pre-fills the dialog with an existing connection, e.g. when editing the output.
     * The password field is only filled if the password is known in this session
     * (it is not stored in the project file).
     */
    public void setSftpConnection(SftpConnection sftpConnection) {
        if (sftpConnection == null) {
            return;
        }
        hostnameTextField.setText(sftpConnection.getHostName());
        hostPortTextField.setText(String.valueOf(sftpConnection.getPort()));
        if (sftpConnection.isLegacyPath()) {
            String[] directoryAndFile = splitLegacyPath(sftpConnection.getPath());
            hostPathTextField.setText(directoryAndFile[0]);
            fileNameTextField.setText(directoryAndFile[1]);
        } else {
            hostPathTextField.setText(sftpConnection.getPath());
            fileNameTextField.setText(sftpConnection.getFileName());
        }
        usernameTextField.setText(sftpConnection.getUserName());
        passwordField.setText(sftpConnection.getPassword());
    }

    private SftpConnection createSftpConnectionFromInput() {
        SftpConnection sftpConnection = new SftpConnection();
        sftpConnection.setHostName(hostnameTextField.getText());
        sftpConnection.setPort(parsePort(hostPortTextField.getText()));
        sftpConnection.setPath(hostPathTextField.getText());
        sftpConnection.setFileName(fileNameTextField.getText() == null ? "" : fileNameTextField.getText().trim());
        sftpConnection.setUserName(usernameTextField.getText());
        sftpConnection.setPassword(passwordField.getText());
        return sftpConnection;
    }

    /**
     * Older project files only contain a single path which may denote a directory or a file.
     * A last path element with an extension (e.g. "/storage/tv.m3u") is treated as file name.
     *
     * @return directory and file name (file name may be empty)
     */
    static String[] splitLegacyPath(String path) {
        if (path == null || path.isEmpty()) {
            return new String[]{"/", ""};
        }
        int lastSlash = path.lastIndexOf('/');
        String lastElement = path.substring(lastSlash + 1);
        if (lastSlash < 0 || !lastElement.contains(".")) {
            return new String[]{path, ""};
        }
        return new String[]{lastSlash == 0 ? "/" : path.substring(0, lastSlash), lastElement};
    }

    /**
     * @return the port, or -1 for non-numeric input (reported by the validation instead of throwing)
     */
    static int parsePort(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.debug("initialize()");
        initializeBinding();
        initializeBrowseButton();
    }

    private void initializeBrowseButton() {
        browseButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> isBlank(hostnameTextField.getText()) || isBlank(usernameTextField.getText())
                        || parsePort(hostPortTextField.getText()) <= 0,
                hostnameTextField.textProperty(), usernameTextField.textProperty(), hostPortTextField.textProperty()));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Opens the {@link SftpBrowserDialog} with the entered connection data and takes over the chosen
     * directory (and file name, if an existing file was selected).
     */
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private void handleBrowse() {
        SftpConnection connection = createSftpConnectionFromInput();
        SftpUserInteraction userInteraction = new SftpUserInteraction();
        if (isBlank(connection.getPassword())) {
            Optional<String> password = userInteraction.requestPassword(
                    connection.getUserName(), connection.getHostName(), connection.getPort());
            if (password.isEmpty()) {
                return;
            }
            connection.setPassword(password.get());
        }
        String path = hostPathTextField.getText();
        String startDirectory = path != null && path.startsWith("/") ? path : ".";

        SftpBrowserDialog browserDialog = new SftpBrowserDialog(connection, startDirectory, userInteraction);
        browserDialog.initOwner(getDialogPane().getScene().getWindow());
        Optional<SftpBrowserDialog.Selection> selection = browserDialog.showAndWait();
        if (browserDialog.isConnected() && isBlank(passwordField.getText())) {
            // the password worked, keep it for this session
            passwordField.setText(connection.getPassword());
        }
        selection.ifPresent(selected -> {
            hostPathTextField.setText(selected.directory());
            if (selected.fileName() != null) {
                fileNameTextField.setText(selected.fileName());
            }
        });
    }

    private void initializeBinding() {
        inputsFull = new BooleanBinding() {
            {
                bind(
                    hostnameTextField.textProperty(),
                    hostPortTextField.textProperty(),
                    hostPathTextField.textProperty(),
                    fileNameTextField.textProperty(),
                    usernameTextField.textProperty(),
                    passwordField.textProperty()
                );
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
                case "fileName" -> markFieldInvalid(fileNameTextField, errorMessage);
                case "userName" -> markFieldInvalid(usernameTextField, errorMessage);
                case "password" -> markFieldInvalid(passwordField, errorMessage);
                default -> throw new IllegalStateException("Unexpected value: " + fieldName);
            }
        }
    }

    private void resetTextFields() {
        resetTextField(hostnameTextField);
        resetTextField(hostPortTextField);
        resetTextField(hostPathTextField);
        resetTextField(fileNameTextField);
        resetTextField(usernameTextField);
        resetTextField(passwordField);
    }

}
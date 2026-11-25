package org.bitpunk.m3uedit.plugins.sftp;

import jakarta.validation.ConstraintViolation;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.Callback;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.bitpunk.m3uedit.plugins.support.PluginDialogController;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.sftp.model.RecentSftpServer;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.plugins.support.BeanValidator;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

@Slf4j
public class SftpConnectionDialogController extends PluginDialogController<SftpConnection> implements Initializable {
    public static final String FXML_PLUGIN_SFTP = "plugin/sftp/SftpConnectionDialog.fxml";

    /**
     * INPUT: the file to read from (must exist, file name required);
     * OUTPUT: the file to write to (empty file name = default, overwrite option).
     */
    public enum Mode {
        INPUT,
        OUTPUT;

        /**
         * @return the title of the dialog, e.g. "SFTP Input: Playlist Source"
         */
        String title(PluginContext context) {
            return this == INPUT ? context.text("sftp.connection.title.input")
                    : context.text("sftp.connection.title.output");
        }
    }

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
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private ComboBox<RecentSftpServer> recentServersComboBox;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Button removeRecentServerButton;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private CheckBox overwriteWithoutAskingCheckBox;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private Label playerBaseUrlLabel;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField playerBaseUrlTextField;
    @Getter
    private BooleanBinding inputsFull;
    private final PluginContext context;
    private final RecentSftpServers recentServers;
    private final Mode mode;

    /**
     * @param context the context of the plugin, providing the texts and the list of recently used servers
     */
    public SftpConnectionDialogController(PluginContext context, Mode mode) {
        super(context, FXML_PLUGIN_SFTP, mode.title(context));
        // initialize() already ran inside super(), so the settings needing the context and the mode are applied here
        this.context = context;
        this.mode = mode;
        this.recentServers = new RecentSftpServers(context);
        setResizable(true);
        setResultConverter(getURIResultConverter());
        initializeRecentServers();
        applyMode();
    }

    private void applyMode() {
        boolean output = mode == Mode.OUTPUT;
        overwriteWithoutAskingCheckBox.setVisible(output);
        overwriteWithoutAskingCheckBox.setManaged(output);
        for (Node node : List.of(playerBaseUrlLabel, playerBaseUrlTextField)) {
            node.setVisible(output);
            node.setManaged(output);
        }
        fileNameTextField.setPromptText(output ? SftpConnection.DEFAULT_FILE_NAME
                : context.text("sftp.connection.fileName.prompt"));
        inputsFull.invalidate();
    }

    private boolean isFileNameRequired() {
        return mode == Mode.INPUT;
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
        overwriteWithoutAskingCheckBox.setSelected(sftpConnection.isOverwriteWithoutAsking());
        // recently used servers do not know the player url, it is kept then
        if (sftpConnection.getPlayerBaseUrl() != null) {
            playerBaseUrlTextField.setText(sftpConnection.getPlayerBaseUrl());
        }
    }

    private SftpConnection createSftpConnectionFromInput() {
        SftpConnection sftpConnection = new SftpConnection();
        sftpConnection.setHostName(hostnameTextField.getText());
        sftpConnection.setPort(parsePort(hostPortTextField.getText()));
        sftpConnection.setPath(hostPathTextField.getText());
        sftpConnection.setFileName(fileNameTextField.getText() == null ? "" : fileNameTextField.getText().trim());
        sftpConnection.setUserName(usernameTextField.getText());
        sftpConnection.setPassword(passwordField.getText());
        sftpConnection.setOverwriteWithoutAsking(overwriteWithoutAskingCheckBox.isSelected());
        if (mode == Mode.OUTPUT) {
            String playerBaseUrl = playerBaseUrlTextField.getText();
            sftpConnection.setPlayerBaseUrl(playerBaseUrl == null || playerBaseUrl.isBlank() ? null : playerBaseUrl.strip());
        }
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

    private void initializeRecentServers() {
        recentServersComboBox.getItems().setAll(recentServers.getServers());
        recentServersComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(RecentSftpServer server) {
                return server == null ? "" : server.displayName();
            }

            @Override
            public RecentSftpServer fromString(String text) {
                return null; // not editable
            }
        });
        recentServersComboBox.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(RecentSftpServer server, boolean empty) {
                super.updateItem(server, empty);
                setText(empty || server == null ? null : server.displayName());
            }
        });
        recentServersComboBox.disableProperty().bind(Bindings.isEmpty(recentServersComboBox.getItems()));
        recentServersComboBox.setOnAction(event -> applyRecentServer(recentServersComboBox.getValue()));
        removeRecentServerButton.disableProperty().bind(recentServersComboBox.valueProperty().isNull());
    }

    /**
     * Fills the fields with a recently used target. The password is kept only if the server and user stay the same.
     */
    private void applyRecentServer(RecentSftpServer server) {
        if (server == null) {
            return;
        }
        SftpConnection current = createSftpConnectionFromInput();
        boolean sameLogin = server.getPort() == current.getPort()
                && server.getHostName().equalsIgnoreCase(Objects.toString(current.getHostName(), ""))
                && server.getUserName().equals(current.getUserName());
        SftpConnection selected = server.toConnection();
        selected.setPassword(sameLogin ? current.getPassword() : null);
        setSftpConnection(selected);
    }

    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private void handleRemoveRecentServer() {
        RecentSftpServer selected = recentServersComboBox.getValue();
        if (selected != null) {
            recentServers.remove(selected);
            recentServersComboBox.setValue(null);
            recentServersComboBox.getItems().remove(selected);
        }
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
        SftpUserInteraction userInteraction = new SftpUserInteraction(context);
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

        SftpBrowserDialog browserDialog = new SftpBrowserDialog(context, connection, startDirectory,
                userInteraction, isFileNameRequired());
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
        if (browserDialog.isConnected()) {
            recentServers.recordSuccess(createSftpConnectionFromInput());
        }
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
        SftpConnection sftpConnectionFromInput = createSftpConnectionFromInput();
        Set<ConstraintViolation<SftpConnection>> errors = BeanValidator.validate(sftpConnectionFromInput);
        displayValidationErrors(errors);
        log.trace("Validation-errors found: {}", errors);
        boolean fileNameMissing = isFileNameRequired() && isBlank(sftpConnectionFromInput.getFileName());
        if (fileNameMissing) {
            markFieldInvalid(fileNameTextField, context.text("sftp.connection.invalid.fileNameEmpty"));
        }
        return errors.isEmpty() && !fileNameMissing && sftpConnectionFromInput.isValidUri();
    }

    private void displayValidationErrors(Set<ConstraintViolation<SftpConnection>> errors) {
        resetTextFields();
        for (ConstraintViolation<SftpConnection> error : errors) {
            String errorMessage = describe(error);
            String fieldName = error.getPropertyPath().toString();
            log.trace("Field: {} Message: {}", fieldName, error.getMessage());
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

    /**
     * @return the message of a validation error in the language of the user interface
     */
    private String describe(ConstraintViolation<SftpConnection> error) {
        return switch (error.getMessage()) {
            case SftpConnection.HOST_NAME_EMPTY -> context.text("sftp.connection.invalid.hostNameEmpty");
            case SftpConnection.PORT_NOT_POSITIVE -> context.text("sftp.connection.invalid.port");
            case SftpConnection.PATH_NOT_ABSOLUTE -> context.text("sftp.connection.invalid.path");
            case SftpConnection.FILE_NAME_WITH_SLASH -> context.text("sftp.connection.invalid.fileName");
            case SftpConnection.USER_NAME_EMPTY -> context.text("sftp.connection.invalid.userNameEmpty");
            default -> error.getMessage();
        };
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
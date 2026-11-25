package org.bitpunk.m3uedit.plugins.sftp;

import javafx.geometry.HPos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import lombok.Getter;
import org.bitpunk.m3uedit.api.PluginContext;

import java.util.Objects;

/**
 * Asks for the password of an SFTP server. It is kept in memory only, until the application is closed.
 */
public class SftpPasswordDialog extends Dialog<String> {
    @Getter
    private final ButtonType connectButtonType;
    // not ButtonType.CANCEL: its text follows the language of the system, not the one of the user interface
    @Getter
    private final ButtonType cancelButtonType;

    @Getter
    private final PasswordField passwordField = new PasswordField();

    /**
     * @param context the context of the plugin, providing the texts
     */
    public SftpPasswordDialog(PluginContext context, String userName, String hostName, int port) {
        connectButtonType = new ButtonType(context.text("sftp.password.connect"), ButtonBar.ButtonData.OK_DONE);
        cancelButtonType = new ButtonType(context.text("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        setTitle(context.text("sftp.password.title"));
        setResizable(false);
        // centered on the window it is opened from instead of anywhere on the screen
        Window.getWindows().stream().filter(Window::isFocused).findFirst().ifPresent(this::initOwner);

        Label server = new Label("%s@%s:%d".formatted(userName, hostName, port));
        server.getStyleClass().add("sftp-password-server");
        passwordField.setPromptText(context.text("sftp.password.prompt"));
        passwordField.setPrefColumnCount(22);
        Label hint = new Label(context.text("sftp.password.hint"));
        hint.getStyleClass().add("sftp-password-hint");
        hint.setWrapText(true);

        GridPane form = new GridPane();
        form.getStyleClass().add("sftp-password-form");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setHalignment(HPos.LEFT);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labels, fields);
        form.addRow(0, new Label(context.text("sftp.password.server")), server);
        form.addRow(1, new Label(context.text("sftp.password.password")), passwordField);
        form.add(hint, 1, 2);

        getDialogPane().setContent(form);
        getDialogPane().getStyleClass().add("sftp-password-dialog");
        getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/plugin/sftp/sftp-password-dialog.css")).toExternalForm());
        getDialogPane().getButtonTypes().setAll(connectButtonType, cancelButtonType);
        getDialogPane().lookupButton(connectButtonType).disableProperty()
                .bind(passwordField.textProperty().isEmpty());
        setResultConverter(button -> button == connectButtonType ? passwordField.getText() : null);
        setOnShown(event -> passwordField.requestFocus());
    }
}

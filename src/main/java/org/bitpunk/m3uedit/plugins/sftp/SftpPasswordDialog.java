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

import java.util.Objects;

/**
 * Asks for the password of an SFTP server. It is kept in memory only, until the application is closed.
 */
public class SftpPasswordDialog extends Dialog<String> {
    public static final ButtonType CONNECT = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
    // not ButtonType.CANCEL: its text follows the language of the system, the application is in English
    public static final ButtonType CANCEL = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    @Getter
    private final PasswordField passwordField = new PasswordField();

    public SftpPasswordDialog(String userName, String hostName, int port) {
        setTitle("SFTP Password");
        setResizable(false);
        // centered on the window it is opened from instead of anywhere on the screen
        Window.getWindows().stream().filter(Window::isFocused).findFirst().ifPresent(this::initOwner);

        Label server = new Label("%s@%s:%d".formatted(userName, hostName, port));
        server.getStyleClass().add("sftp-password-server");
        passwordField.setPromptText("password");
        passwordField.setPrefColumnCount(22);
        Label hint = new Label("The password is kept until m3uedit is closed, it is never stored.");
        hint.getStyleClass().add("sftp-password-hint");
        hint.setWrapText(true);

        GridPane form = new GridPane();
        form.getStyleClass().add("sftp-password-form");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setHalignment(HPos.LEFT);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labels, fields);
        form.addRow(0, new Label("Server"), server);
        form.addRow(1, new Label("Password"), passwordField);
        form.add(hint, 1, 2);

        getDialogPane().setContent(form);
        getDialogPane().getStyleClass().add("sftp-password-dialog");
        getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/plugin/sftp/sftp-password-dialog.css")).toExternalForm());
        getDialogPane().getButtonTypes().setAll(CONNECT, CANCEL);
        getDialogPane().lookupButton(CONNECT).disableProperty().bind(passwordField.textProperty().isEmpty());
        setResultConverter(button -> button == CONNECT ? passwordField.getText() : null);
        setOnShown(event -> passwordField.requestFocus());
    }
}

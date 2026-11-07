package org.bitpunk.m3uedit.plugins.output.sftp;

import com.jcraft.jsch.UserInfo;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * JavaFX based user interaction for SFTP connections:
 * confirmation of unknown host keys (via JSch {@link UserInfo}) and the password prompt.
 * All dialogs are shown on the JavaFX application thread, regardless of the calling thread.
 */
public class SftpUserInteraction implements UserInfo {
    private static final Logger log = LoggerFactory.getLogger(SftpUserInteraction.class);

    /**
     * Asks the user for the SFTP password.
     *
     * @return the entered password, or empty if the dialog was cancelled
     */
    public Optional<String> requestPassword(String userName, String hostName, int port) {
        return callOnFxThread(() -> {
            PasswordField passwordField = new PasswordField();
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("SFTP Password");
            dialog.setHeaderText("Password for " + userName + "@" + hostName + ":" + port);
            dialog.getDialogPane().setContent(passwordField);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.getDialogPane().lookupButton(ButtonType.OK)
                    .disableProperty().bind(passwordField.textProperty().isEmpty());
            dialog.setResultConverter(button -> button == ButtonType.OK ? passwordField.getText() : null);
            Platform.runLater(passwordField::requestFocus);
            return dialog.showAndWait();
        });
    }

    /**
     * Called by JSch for unknown hosts (and for creating a missing known_hosts file).
     * The message already contains the host key fingerprint.
     */
    @Override
    public boolean promptYesNo(String message) {
        log.info("Host key confirmation requested: {}", message);
        boolean accepted = callOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
            alert.setTitle("SFTP Host Verification");
            alert.setHeaderText("Please verify the identity of the SFTP server");
            return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
        });
        log.info("Host key confirmation {}", accepted ? "accepted" : "rejected");
        return accepted;
    }

    @Override
    public void showMessage(String message) {
        log.warn("SFTP server message: {}", message);
        callOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            alert.setTitle("SFTP");
            alert.setHeaderText(null);
            alert.showAndWait();
            return null;
        });
    }

    // Password authentication uses Session#setPassword, key passphrases are not supported yet.
    @Override
    public String getPassphrase() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean promptPassword(String message) {
        return false;
    }

    @Override
    public boolean promptPassphrase(String message) {
        return false;
    }

    private static <T> T callOnFxThread(Supplier<T> supplier) {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for user input", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("User dialog failed", e.getCause());
        }
    }
}

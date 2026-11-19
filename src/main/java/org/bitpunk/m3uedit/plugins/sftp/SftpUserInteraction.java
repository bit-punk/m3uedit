package org.bitpunk.m3uedit.plugins.sftp;

import lombok.extern.slf4j.Slf4j;
import com.jcraft.jsch.UserInfo;
import org.bitpunk.m3uedit.util.HeadlessMode;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * JavaFX based user interaction for SFTP connections:
 * confirmation of unknown host keys (via JSch {@link UserInfo}) and the password prompt.
 * All dialogs are shown on the JavaFX application thread, regardless of the calling thread.
 */
@Slf4j
public class SftpUserInteraction implements UserInfo {
    /**
     * @return the user interaction showing dialogs, or the one without dialogs in headless mode
     */
    public static SftpUserInteraction create() {
        return HeadlessMode.isActive() ? new HeadlessSftpUserInteraction() : new SftpUserInteraction();
    }

    public enum OverwriteDecision {
        CANCEL, OVERWRITE, OVERWRITE_ALWAYS
    }

    /**
     * Asks whether an existing file on the server may be overwritten.
     *
     * @param target           e.g. "root@host:22/storage/tv.m3u"
     * @param existingFileInfo size / modification date of the existing file
     */
    public OverwriteDecision confirmOverwrite(String target, String existingFileInfo) {
        return callOnFxThread(() -> {
            ButtonType overwrite = new ButtonType("Overwrite", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            CheckBox dontAskAgain = new CheckBox("Don't ask again for this target");
            Label message = new Label(target + "\n" + existingFileInfo + "\n\nDo you want to overwrite it?");
            message.setWrapText(true);

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, overwrite, cancel);
            alert.setTitle("SFTP Output");
            alert.setHeaderText("The playlist already exists on the server");
            alert.getDialogPane().setContent(new VBox(10, message, dontAskAgain));
            // never overwrite by accidentally pressing Enter
            ((Button) alert.getDialogPane().lookupButton(overwrite)).setDefaultButton(false);
            ((Button) alert.getDialogPane().lookupButton(cancel)).setDefaultButton(true);

            boolean confirmed = alert.showAndWait().filter(overwrite::equals).isPresent();
            if (!confirmed) {
                return OverwriteDecision.CANCEL;
            }
            return dontAskAgain.isSelected() ? OverwriteDecision.OVERWRITE_ALWAYS : OverwriteDecision.OVERWRITE;
        });
    }

    /**
     * The password is not persisted in the project file, so it has to be requested once per session.
     * An entered password is stored in the connection (in memory only).
     *
     * @return true if a password is available
     */
    public boolean ensurePassword(SftpConnection connection) {
        String password = connection.getPassword();
        if (password != null && !password.isEmpty()) {
            return true;
        }
        Optional<String> entered = requestPassword(connection.getUserName(), connection.getHostName(), connection.getPort());
        entered.ifPresent(connection::setPassword);
        return entered.isPresent();
    }

    /**
     * Asks the user for the SFTP password.
     *
     * @return the entered password, or empty if the dialog was cancelled
     */
    public Optional<String> requestPassword(String userName, String hostName, int port) {
        return callOnFxThread(() -> new SftpPasswordDialog(userName, hostName, port).showAndWait());
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

package org.bitpunk.m3uedit.plugins.sftp;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SftpPasswordDialogTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test
    void testTheServerIsNamedAndTheButtonsAreInEnglish() throws Exception {
        List<String> texts = onFxThread(() -> {
            SftpPasswordDialog dialog = new SftpPasswordDialog("root", "192.168.178.22", 22);
            return List.of(((Label) dialog.getDialogPane().lookup(".sftp-password-server")).getText(),
                    ((Button) dialog.getDialogPane().lookupButton(SftpPasswordDialog.CONNECT)).getText(),
                    ((Button) dialog.getDialogPane().lookupButton(SftpPasswordDialog.CANCEL)).getText());
        });

        assertThat(texts).containsExactly("root@192.168.178.22:22", "Connect", "Cancel");
    }

    @Test
    void testConnectNeedsAPassword() throws Exception {
        List<Boolean> disabled = onFxThread(() -> {
            SftpPasswordDialog dialog = new SftpPasswordDialog("root", "nas", 22);
            boolean empty = dialog.getDialogPane().lookupButton(SftpPasswordDialog.CONNECT).isDisabled();
            dialog.getPasswordField().setText("secret");
            return List.of(empty, dialog.getDialogPane().lookupButton(SftpPasswordDialog.CONNECT).isDisabled());
        });

        assertThat(disabled).containsExactly(true, false);
    }

    @Test
    void testThePasswordIsTheResultOfConnectOnly() throws Exception {
        List<String> results = onFxThread(() -> {
            SftpPasswordDialog dialog = new SftpPasswordDialog("root", "nas", 22);
            dialog.getPasswordField().setText("secret");
            return java.util.Arrays.asList(dialog.getResultConverter().call(SftpPasswordDialog.CONNECT),
                    dialog.getResultConverter().call(SftpPasswordDialog.CANCEL));
        });

        assertThat(results).containsExactly("secret", null);
    }
}

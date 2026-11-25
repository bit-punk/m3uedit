package org.bitpunk.m3uedit.plugins.input.sftp;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.InputReadException;
import org.bitpunk.m3uedit.api.Playlist;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.sftp.RecentSftpServers;
import org.bitpunk.m3uedit.plugins.sftp.SftpClient;
import org.bitpunk.m3uedit.plugins.sftp.SftpConnectionDialogController;
import org.bitpunk.m3uedit.plugins.sftp.SftpUserInteraction;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnectionSettings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

/**
 * Reads a playlist from an SFTP server. Shares connection handling, dialogs and the list of
 * recently used servers with the SFTP output.
 * <p>
 * The password of the connection is kept in memory only, it is not part of the settings stored in project-files.
 */
@Slf4j
@PluginType(id = "SftpInput", nameKey = "input.sftp.name", texts = "i18n.messages")
public class SftpInput implements Input {

    private PluginContext context;
    @Getter
    @Setter
    private SftpConnection sftpConnection;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("input.sftp.name");
    }

    @Override
    public String getDescription() {
        return context.text("input.sftp.description");
    }

    @Override
    public Class<? extends Record> getSettingsType() {
        return SftpInputSettings.class;
    }

    @Override
    public Record getSettings() {
        return new SftpInputSettings(SftpConnectionSettings.of(sftpConnection));
    }

    /**
     * Sets the connection; the password known so far is kept if the server and the user stay the same.
     */
    @Override
    public void setSettings(Record settings) {
        SftpConnectionSettings connection = settings instanceof SftpInputSettings inputSettings
                ? inputSettings.sftpConnection() : null;
        sftpConnection = connection == null ? null : connection.toConnection(sftpConnection);
    }

    /**
     * Reads the playlist. Errors are reported by the pipeline together with the errors of the other inputs,
     * so a temporarily unreachable server does not break loading the other inputs.
     */
    @Override
    public Playlist read() {
        if (sftpConnection == null) {
            return Playlist.empty();
        }
        SftpUserInteraction userInteraction = createUserInteraction();
        String target = describeTarget();
        if (!userInteraction.ensurePassword(sftpConnection)) {
            log.warn("Reading from {} skipped: no password entered", target);
            throw new InputReadException(context.text("input.sftp.noPassword", target), List.of());
        }
        String path = sftpConnection.getTargetPath();
        try (SftpClient client = SftpClient.connect(sftpConnection, userInteraction);
             InputStream inputStream = client.channel().get(path)) {
            Playlist playlist = context.readPlaylist(inputStream);
            log.info("Read {} entries from {}", playlist.entries().size(), target);
            recordRecentServer();
            return playlist;
        } catch (JSchException e) {
            if (SftpClient.isAuthenticationFailure(e)) {
                // ask again on the next attempt instead of retrying a wrong password
                sftpConnection.setPassword(null);
            }
            throw readError(target, e);
        } catch (SftpException | IOException e) {
            throw readError(target, e);
        }
    }

    /**
     * The error is reported by the pipeline together with the errors of the other inputs, not by a dialog of its own.
     */
    private InputReadException readError(String target, Exception e) {
        log.error("Reading playlist from {} failed: {}", target, e.getMessage(), e);
        return new InputReadException(SftpClient.describeError(context, e), e);
    }

    private String describeTarget() {
        return sftpConnection.getUserName() + "@" + sftpConnection.getHostName() + ":" + sftpConnection.getPort()
                + sftpConnection.getTargetPath();
    }

    protected SftpUserInteraction createUserInteraction() {
        return SftpUserInteraction.create(context);
    }

    /**
     * Remembers the source in the "recently used servers" list (shared with the SFTP output);
     * failures there must not break reading.
     */
    protected void recordRecentServer() {
        try {
            new RecentSftpServers(context).recordSuccess(sftpConnection);
        } catch (RuntimeException e) {
            log.warn("Could not update list of recently used SFTP servers: {}", e.getMessage(), e);
        }
    }

    /**
     * @return the source as URI without password, e.g. "sftp://root@192.168.178.22:22/storage/tv.m3u"
     */
    @Override
    public String getSource() {
        try {
            return sftpConnection != null ? sftpConnection.toURI().toString() : null;
        } catch (URISyntaxException e) {
            log.warn("Invalid SFTP source: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean configure(Window owner) {
        log.debug("configure()");
        SftpConnectionDialogController dialogController = new SftpConnectionDialogController(context,
                SftpConnectionDialogController.Mode.INPUT);
        if (owner != null) {
            dialogController.initOwner(owner);
        }
        dialogController.setSftpConnection(sftpConnection);
        DialogPane dialogPane = dialogController.getDialogPane();
        dialogPane.lookupButton(dialogController.okButtonType)
                .disableProperty()
                .bind(dialogController.getInputsFull().not());
        Optional<SftpConnection> connection = dialogController.showAndWait();
        connection.ifPresent(selected -> sftpConnection = selected);
        return connection.isPresent();
    }

    /**
     * Only a short description, never the connection: it may contain the password.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}

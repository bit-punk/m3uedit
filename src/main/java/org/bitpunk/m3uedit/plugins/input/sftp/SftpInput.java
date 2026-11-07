package org.bitpunk.m3uedit.plugins.input.sftp;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import javafx.scene.control.DialogPane;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.sftp.RecentSftpServers;
import org.bitpunk.m3uedit.plugins.sftp.SftpClient;
import org.bitpunk.m3uedit.plugins.sftp.SftpConnectionDialogController;
import org.bitpunk.m3uedit.plugins.sftp.SftpUserInteraction;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a playlist from an SFTP server. Shares connection handling, dialogs and the list of
 * recently used servers with the SFTP output.
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
public class SftpInput extends AbstractInput implements Input {

    @XStreamOmitField
    private final String name = "SFTP Input";
    @XStreamOmitField
    private final String description = "Reads a playlist from a SFTP server.";
    private SftpConnection sftpConnection;

    /**
     * Reads the playlist. Errors are shown to the user and result in an empty list,
     * so a temporarily unreachable server does not break loading the other inputs.
     */
    @Override
    public List<Entry> read() {
        if (sftpConnection == null) {
            return new ArrayList<>();
        }
        SftpUserInteraction userInteraction = createUserInteraction();
        String target = describeTarget();
        if (!userInteraction.ensurePassword(sftpConnection)) {
            log.warn("Reading from {} skipped: no password entered", target);
            return new ArrayList<>();
        }
        String path = sftpConnection.getTargetPath();
        try (SftpClient client = SftpClient.connect(sftpConnection, userInteraction);
             InputStream inputStream = client.channel().get(path)) {
            List<Entry> entries = readData(inputStream);
            log.info("Read {} entries from {}", entries.size(), target);
            recordRecentServer();
            return entries;
        } catch (JSchException e) {
            if (SftpClient.isAuthenticationFailure(e)) {
                // ask again on the next attempt instead of retrying a wrong password
                sftpConnection.setPassword(null);
            }
            return handleReadError(userInteraction, target, e);
        } catch (SftpException | IOException e) {
            return handleReadError(userInteraction, target, e);
        }
    }

    private List<Entry> handleReadError(SftpUserInteraction userInteraction, String target, Exception e) {
        log.error("Reading playlist from {} failed: {}", target, e.getMessage(), e);
        userInteraction.showMessage("Reading the playlist from " + target + " failed:\n" + SftpClient.describeError(e));
        return new ArrayList<>();
    }

    private String describeTarget() {
        return sftpConnection.getUserName() + "@" + sftpConnection.getHostName() + ":" + sftpConnection.getPort()
                + sftpConnection.getTargetPath();
    }

    protected SftpUserInteraction createUserInteraction() {
        return new SftpUserInteraction();
    }

    /**
     * Remembers the source in the "recently used servers" list (shared with the SFTP output);
     * failures there must not break reading.
     */
    protected void recordRecentServer() {
        try {
            new RecentSftpServers().recordSuccess(sftpConnection);
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
    public void configure() {
        log.debug("configure()");
        SftpConnectionDialogController dialogController = new SftpConnectionDialogController(SftpConnectionDialogController.Mode.INPUT);
        dialogController.setSftpConnection(sftpConnection);
        DialogPane dialogPane = dialogController.getDialogPane();
        dialogPane.lookupButton(dialogController.okButtonType)
                .disableProperty()
                .bind(dialogController.getInputsFull().not());
        dialogController.showAndWait().ifPresent(connection -> sftpConnection = connection);
    }
}

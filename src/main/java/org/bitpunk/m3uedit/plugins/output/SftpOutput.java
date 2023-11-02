package org.bitpunk.m3uedit.plugins.output;

import com.jcraft.jsch.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.DialogPane;
import lombok.Getter;
import org.bitpunk.m3uedit.plugins.output.sftp.SftpTargetDialogController;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.plugins.output.sftp.model.SftpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Getter
public class SftpOutput implements Output {
    private static final Logger log = LoggerFactory.getLogger(SftpOutput.class);
    private final String name = "SFTP Output";
    private final String description = "writes a playlist to a SFTP server.";
    private SftpConnection sftpConnection = null;

    @Override
    public void write(List<Entry> playlist) {
        log.debug("write()");
        if (playlist != null && !playlist.isEmpty() && sftpConnection != null) {
            ChannelSftp channelSftp;
            try {
                channelSftp = setupJsch();
                channelSftp.connect();
                Writer writer = new Writer();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                writer.write(playlist, outputStream);

                ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

                channelSftp.put(inputStream, sftpConnection.getPath());
            } catch (JSchException | SftpException e) {
                throw new RuntimeException(e);
            }
            channelSftp.exit();
        }
    }

    private ChannelSftp setupJsch() throws JSchException {
        ChannelSftp channelSftp = null;
        if (sftpConnection != null) {
            JSch jsch = new JSch();
            jsch.setKnownHosts("~/.ssh/known_hosts");
            Session jschSession = jsch.getSession(sftpConnection.getUserName(), sftpConnection.getHostName(), sftpConnection.getPort());
            jschSession.setPassword(sftpConnection.getPassword());
            jschSession.connect();
            channelSftp = (ChannelSftp) jschSession.openChannel("sftp");
        }
        return channelSftp;
    }

    @Override
    public void configure() {
        log.debug("configure()");
        SftpTargetDialogController dialogController;
        DialogPane dialogPane;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("plugin/output/sftp/SftpTargetDialog.fxml"));
            dialogPane = loader.load();
            dialogController = loader.getController();
            dialogController.setDialogPane(dialogPane);

            dialogPane.lookupButton(dialogController.okButtonType)
                    .disableProperty()
                    .bind(dialogController.getInputsFull().not());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dialogController.showAndWait().ifPresent(uri -> sftpConnection = uri);
    }

    @Override
    public URI getOutput() {
        try {
            return sftpConnection != null ? sftpConnection.toURI() : null;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
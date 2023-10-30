package org.bitpunk.m3uedit.plugins.output;

import com.jcraft.jsch.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.DialogPane;
import lombok.Getter;
import org.bitpunk.m3uedit.dialog.SftpTargetDialogController;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;

@Getter
public class SftpOutput implements Output {
    private static final Logger log = LoggerFactory.getLogger(SftpOutput.class);
    private final String name = "SFTP Output";
    private final String description = "writes a playlist to a SFTP server.";
    private URI output = null;

    @Override
    public void write(List<Entry> playlist) {
        log.debug("write()");
        if (playlist != null && !playlist.isEmpty() && output != null) {
            ChannelSftp channelSftp;
            try {
                channelSftp = setupJsch();
                channelSftp.connect();
                Writer writer = new Writer();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                writer.write(playlist, outputStream);

                ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

                channelSftp.put(inputStream, output.getPath());
            } catch (JSchException | SftpException e) {
                throw new RuntimeException(e);
            }
            channelSftp.exit();
        }
    }

    private ChannelSftp setupJsch() throws JSchException {
        ChannelSftp channelSftp = null;
        if (output != null) {
            JSch jsch = new JSch();
            jsch.setKnownHosts("~/.ssh/known_hosts");
            String userInfo = output.getUserInfo();     // username:password
            String[] userInfoArray = userInfo.split(":");
            Session jschSession = jsch.getSession(userInfoArray[0], output.getHost(), output.getPort());
            jschSession.setPassword(userInfoArray[1]);
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
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("dialog/SftpTargetDialog.fxml"));
            dialogPane = loader.load();
            dialogController = loader.getController();
            dialogController.setDialogPane(dialogPane);

            dialogPane.lookupButton(dialogController.okButtonType)
                    .disableProperty()
                    .bind(dialogController.getInputsFull().not());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dialogController.showAndWait().ifPresent(uri -> output = uri);
    }
}
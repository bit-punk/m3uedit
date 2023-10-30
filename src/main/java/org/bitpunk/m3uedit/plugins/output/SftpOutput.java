package org.bitpunk.m3uedit.plugins.output;

import com.jcraft.jsch.*;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import lombok.Getter;
import org.bitpunk.m3uedit.dialog.SftpTargetDialogController;
import org.bitpunk.m3uedit.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;

@Getter
public class SftpOutput implements Output {
    private static final Logger log = LoggerFactory.getLogger(SftpOutput.class);
    private final String name = "SFTP Output";
    private final String description = "writes a playlist to a SFTP server.";
    private final URI output = null;

    @Override
    public void write(List<Entry> playlist) {
        log.debug("write()");
        ChannelSftp channelSftp;
        try {
            channelSftp = setupJsch();
        channelSftp.connect();

        String localFile = "src/main/resources/sample.txt";
        String remoteDir = "remote_sftp_test/";

        channelSftp.put(localFile, remoteDir + "jschFile.txt");
        } catch (JSchException | SftpException e) {
            throw new RuntimeException(e);
        }

        channelSftp.exit();
    }

    private ChannelSftp setupJsch() throws JSchException {
        String username = "jochen";
        String password = "erbsensuppe";
        String remoteHost = "127.0.0.1";
        JSch jsch = new JSch();
        jsch.setKnownHosts("~/.ssh/known_hosts");
        Session jschSession = jsch.getSession(username, remoteHost);
        jschSession.setPassword(password);
        jschSession.connect();
        return (ChannelSftp) jschSession.openChannel("sftp");
    }

    @Override
    public void configure() {
        log.debug("configure()");
        SftpTargetDialogController dialogController = new SftpTargetDialogController();
        DialogPane dialogPane;
        try {
            dialogPane = FXMLLoader.load(Objects.requireNonNull(getClass().getClassLoader().getResource("dialog/SftpTargetDialog.fxml")));
            dialogController.setDialogPane(dialogPane);

            ObservableList<ButtonType> buttonTypes = dialogPane.getButtonTypes();
            log.debug("existing ButtonTypes: {}", buttonTypes);
            ButtonType okButtonType = buttonTypes.get(0);
            log.debug("lookup ButtonType: {}", okButtonType);

//            dialogPane.lookupButton(okButtonType).disableProperty()
//                    .bind(dialogController.inputsFullBinding().not());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dialogController.showAndWait().ifPresent(uri -> log.debug("returned URI from Dialog: [{}]", uri));
    }
}
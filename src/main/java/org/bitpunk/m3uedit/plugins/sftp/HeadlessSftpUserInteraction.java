package org.bitpunk.m3uedit.plugins.sftp;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The user interaction of SFTP connections in headless mode, without dialogs:
 * <ul>
 *     <li>the password is taken from the environment variable of the connection, e.g.
 *     {@code M3UEDIT_SFTP_PASSWORD_ROOT_192_168_178_22} for {@code root@192.168.178.22} (see
 *     {@link #passwordVariable(String, String)}), or else from {@value #PASSWORD_VARIABLE}</li>
 *     <li>unknown host keys are rejected, the host has to be known from a connection made with the user interface</li>
 *     <li>existing files are overwritten without asking</li>
 *     <li>messages of the server are logged</li>
 * </ul>
 */
@Slf4j
public class HeadlessSftpUserInteraction extends SftpUserInteraction {
    public static final String PASSWORD_VARIABLE = "M3UEDIT_SFTP_PASSWORD";

    private final UnaryOperator<String> environment;

    public HeadlessSftpUserInteraction() {
        this(System::getenv);
    }

    /**
     * @param environment returns the value of an environment variable, null if it is not set
     */
    HeadlessSftpUserInteraction(UnaryOperator<String> environment) {
        // no dialogs, so no texts are needed
        super(null);
        this.environment = environment;
    }

    @Override
    public OverwriteDecision confirmOverwrite(String target, String existingFileInfo) {
        log.info("Overwriting {} ({})", target, existingFileInfo);
        return OverwriteDecision.OVERWRITE;
    }

    @Override
    public Optional<String> requestPassword(String userName, String hostName, int port) {
        String variable = passwordVariable(userName, hostName);
        for (String name : List.of(variable, PASSWORD_VARIABLE)) {
            String password = environment.apply(name);
            if (password != null && !password.isEmpty()) {
                log.info("Using the password of {}@{}:{} from {}", userName, hostName, port, name);
                return Optional.of(password);
            }
        }
        log.error("No password for {}@{}:{}: set the environment variable {} (or {} for all servers)", userName,
                hostName, port, variable, PASSWORD_VARIABLE);
        return Optional.empty();
    }

    /**
     * @return the environment variable with the password of the given connection, e.g.
     * {@code M3UEDIT_SFTP_PASSWORD_ROOT_192_168_178_22} for {@code root@192.168.178.22}: upper case, all characters
     * except letters and digits replaced by {@code _}, so the name is valid in every shell
     */
    public static String passwordVariable(String userName, String hostName) {
        return PASSWORD_VARIABLE + "_" + toVariablePart(userName) + "_" + toVariablePart(hostName);
    }

    private static String toVariablePart(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    @Override
    public boolean ensurePassword(SftpConnection connection) {
        if (connection.getPassword() != null && !connection.getPassword().isEmpty()) {
            return true;
        }
        Optional<String> password = requestPassword(connection.getUserName(), connection.getHostName(),
                connection.getPort());
        password.ifPresent(connection::setPassword);
        return password.isPresent();
    }

    @Override
    public boolean promptYesNo(String message) {
        log.error("Unknown host key rejected, connect to the server once using the user interface to accept it: {}",
                message);
        return false;
    }

    @Override
    public void showMessage(String message) {
        log.warn("SFTP: {}", message);
    }
}

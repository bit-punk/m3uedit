package org.bitpunk.m3uedit.plugins.output.sftp.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

@Data
public class SftpConnection {
    public static final String SFTP_SCHEMA = "sftp";
    public static final String REGEX_PATH = "^/.*$";
    private static final Logger log = LoggerFactory.getLogger(SftpConnection.class);
    private String schema = SFTP_SCHEMA;
    @NotEmpty(message = "Hostname must not be empty")
    private String hostName;
    @Positive(message = "Port must be a positive integer")
    @Digits(message = "", integer = 5, fraction = 0)
    //    @Pattern(regexp = "\\d*", message = "The port-number must be a numeric input!")
    private int port;
    @Pattern(regexp = REGEX_PATH, message = "Path must start with '/'")
    private String path;
    @NotEmpty(message = "Username must not be empty")
    private String userName;
    @NotEmpty(message = "Password must not be empty")
    private String password;

    public URI toURI() throws URISyntaxException {
        return new URI(schema, userName + ":" + password, hostName, port, path, null, null);
    }

    public boolean isValidUri() {
        try {
            toURI();
            return true;
        } catch (URISyntaxException e) {
            log.debug("No valid URI input-data: {}", e.getMessage());
            return false;
        }
    }

}
package org.bitpunk.m3uedit.plugins.output.sftp.model;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

@Data
public class SftpConnection {
    public static final String SFTP_SCHEMA = "sftp";
    public static final String REGEX_PATH = "^/.*$";
    public static final String REGEX_FILE_NAME = "^[^/]*$";
    public static final String DEFAULT_FILE_NAME = "playlist.m3u";
    private static final Logger log = LoggerFactory.getLogger(SftpConnection.class);
    private String schema = SFTP_SCHEMA;
    @NotEmpty(message = "Hostname must not be empty")
    private String hostName;
    @Positive(message = "Port must be a positive integer")
    @Digits(message = "", integer = 5, fraction = 0)
    //    @Pattern(regexp = "\\d*", message = "The port-number must be a numeric input!")
    private int port;
    /**
     * Target directory. In project files written by older versions (where {@link #fileName} is null)
     * this may also be the complete file path.
     */
    @Pattern(regexp = REGEX_PATH, message = "Path must start with '/'")
    private String path;
    /**
     * Target file name inside {@link #path}; empty means {@link #DEFAULT_FILE_NAME}.
     * Null for connections from older project files.
     */
    @Pattern(regexp = REGEX_FILE_NAME, message = "Filename must not contain '/'")
    private String fileName;
    @NotEmpty(message = "Username must not be empty")
    private String userName;
    /**
     * If false, the user is asked before an existing file on the server is overwritten.
     */
    private boolean overwriteWithoutAsking;
    /**
     * Kept in memory only: never persisted to the project file and never logged.
     * Optional: if empty (e.g. after loading a project) the user is asked for it on the first write.
     */
    @XStreamOmitField
    @ToString.Exclude
    private String password;

    /**
     * @return the connection as URI <b>without</b> the password, safe for display.
     */
    public URI toURI() throws URISyntaxException {
        return new URI(schema, userName, hostName, port, getTargetPath(), null, null);
    }

    /**
     * @return true if this connection comes from an older project file without separate file name
     */
    public boolean isLegacyPath() {
        return fileName == null;
    }

    /**
     * @return directory and file name joined to the full target path
     * (for legacy connections the configured path as is)
     */
    public String getTargetPath() {
        if (isLegacyPath()) {
            return path;
        }
        String name = fileName.isBlank() ? DEFAULT_FILE_NAME : fileName.trim();
        String directory = path == null || path.isEmpty() ? "/" : path;
        return directory.endsWith("/") ? directory + name : directory + "/" + name;
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
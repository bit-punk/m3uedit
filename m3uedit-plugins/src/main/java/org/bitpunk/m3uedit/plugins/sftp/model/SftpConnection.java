package org.bitpunk.m3uedit.plugins.sftp.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.ToString;

import java.net.URI;
import java.net.URISyntaxException;

@Data
@Slf4j
public class SftpConnection {
    public static final String SFTP_SCHEMA = "sftp";
    public static final String REGEX_PATH = "^/.*$";
    public static final String REGEX_FILE_NAME = "^[^/]*$";
    public static final String DEFAULT_FILE_NAME = "playlist.m3u";
    /**
     * The messages of the validation, translated when they are shown.
     */
    public static final String HOST_NAME_EMPTY = "Hostname must not be empty";
    public static final String PORT_NOT_POSITIVE = "Port must be a positive integer";
    public static final String PATH_NOT_ABSOLUTE = "Path must start with '/'";
    public static final String FILE_NAME_WITH_SLASH = "Filename must not contain '/'";
    public static final String USER_NAME_EMPTY = "Username must not be empty";
    private String schema = SFTP_SCHEMA;
    @NotEmpty(message = HOST_NAME_EMPTY)
    private String hostName;
    @Positive(message = PORT_NOT_POSITIVE)
    @Digits(message = "", integer = 5, fraction = 0)
    //    @Pattern(regexp = "\\d*", message = "The port-number must be a numeric input!")
    private int port;
    /**
     * Target directory. In project files written by older versions (where {@link #fileName} is null)
     * this may also be the complete file path.
     */
    @Pattern(regexp = REGEX_PATH, message = PATH_NOT_ABSOLUTE)
    private String path;
    /**
     * Target file name inside {@link #path}; empty means {@link #DEFAULT_FILE_NAME}.
     * Null for connections from older project files.
     */
    @Pattern(regexp = REGEX_FILE_NAME, message = FILE_NAME_WITH_SLASH)
    private String fileName;
    @NotEmpty(message = USER_NAME_EMPTY)
    private String userName;
    /**
     * If false, the user is asked before an existing file on the server is overwritten.
     */
    private boolean overwriteWithoutAsking;
    /**
     * Output only: the url players read the target directory from (e.g. a web server serving it), used to reference
     * the additional files written next to the playlist, e.g. the programme guide. Optional.
     */
    private String playerBaseUrl;
    /**
     * Kept in memory only: never persisted to the project file (it is not part of {@link SftpConnectionSettings}) and
     * never logged. Optional: if empty (e.g. after loading a project) the user is asked for it on the first write.
     */
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
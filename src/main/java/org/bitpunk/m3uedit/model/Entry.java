package org.bitpunk.m3uedit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.bitpunk.m3uedit.NamedItem;

import java.util.Map;

/**
 * A channel of a playlist.
 * <p>
 * Entries are compared by identity: a playlist may contain several entries with the same values (e.g. the same
 * channel from two inputs), and removing or moving one of them must not affect the others.
 */
@Getter
@Setter
@ToString
public class Entry implements NamedItem {
    public static final String REGEX_URL = "^(https?|ftp|file|rtsp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
    public static final String REGEX_NUMERIC = "[+-]?\\d*";

    /**
     * The position shown in the playlist, not part of the project-file.
     */
    @JsonIgnore
    @Min(value = 1, message = "Index must be greater than 0")
    private int index;
    @Pattern(regexp = REGEX_NUMERIC, message = "Duration must be a numeric value")
    private String duration;
    private String groupTitle;
    private String tvgId;
    private String tvgName;
    private String tvgLogo;
    private String tvgShift;
    private String radio;
    @Pattern(regexp = REGEX_URL, message = "You must specify a valid url")
    private String channelUri;
    @NotEmpty(message = "Name cannot be empty")
    private String name;
    private Map<String, String> vlcOpts;
}
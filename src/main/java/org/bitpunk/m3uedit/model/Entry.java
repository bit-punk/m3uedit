package org.bitpunk.m3uedit.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.bitpunk.m3uedit.NamedItem;

import java.util.Map;

@Data
public class Entry implements NamedItem {
    public static final String REGEX_URL = "^(https?|ftp|file|rtsp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
    public static final String REGEX_NUMERIC = "[+-]?\\d*";
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
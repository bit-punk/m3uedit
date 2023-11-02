package org.bitpunk.m3uedit.model;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.bitpunk.m3uedit.NamedItem;

import java.util.Map;

@Data
public class Entry implements NamedItem {
    @Pattern(regexp = "\\d*", message = "Duration must be a numeric value")
    private String duration;
    private String groupTitle;
    private String tvgId;
    private String tvgName;
    private String tvgLogo;
    private String tvgShift;
    private String radio;
    @Pattern(
            regexp = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
            message = "You must specify a valid url"
    )
    private String channelUri;
    private String name;
    private Map<String, String> vlcOpts;
}
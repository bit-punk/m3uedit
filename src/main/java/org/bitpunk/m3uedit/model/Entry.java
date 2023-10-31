package org.bitpunk.m3uedit.model;

import lombok.Data;
import org.bitpunk.m3uedit.NamedItem;

import java.util.Map;

@Data
public class Entry implements NamedItem {
    private String duration;
    private String groupTitle;
    private String tvgId;
    private String tvgName;
    private String tvgLogo;
    private String tvgShift;
    private String radio;
    private String channelUri;
    private String name;
    private Map<String, String> vlcOpts;
}
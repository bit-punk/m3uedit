package org.bitpunk.m3uedit.builder;

import org.bitpunk.m3uedit.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Builder {
    private static final Logger log = LoggerFactory.getLogger(Builder.class);

    private String duration;
    private String groupTitle;
    private String tvgId;
    private String tvgName;
    private String tvgLogo;
    private String tvgShift;
    private String radio;
    private String channelUri;
    private String channelName;

    private final Map<String, String> vlcOpts = new HashMap<>();

    public Builder duration(String duration) {
        this.duration = duration;
        return this;
    }

    public Builder groupTitle(String groupTitle) {
        this.groupTitle = groupTitle;
        return this;
    }

    public Builder tvgId(String tvgId) {
        this.tvgId = tvgId;
        return this;
    }

    public Builder tvgName(String tvgName) {
        this.tvgName = tvgName;
        return this;
    }

    public Builder tvgLogo(String tvgLogo) {
        this.tvgLogo = tvgLogo;
        return this;
    }

    public Builder tvgShift(String tvgShift) {
        this.tvgShift = tvgShift;
        return this;
    }

    public Builder radio(String radio) {
        this.radio = radio;
        return this;
    }

    public Builder channelUri(String channelUri) {
        this.channelUri = channelUri;
        return this;
    }

    public Builder channelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public Builder vlcOptions(Map<String, String> options) {
        for (Map.Entry<String, String> option : options.entrySet()) {
            String key = option.getKey();
            String value = option.getValue();
            String previousValue = this.vlcOpts.put(key, value);
            if (previousValue != null) {
                log.warn("Previous value for key {} got re-assigned ({} -> {})", key, previousValue, value);
            }
        }
        return this;
    }

    public Entry build() {
        Entry entry = new Entry();
        entry.setDuration(duration);
        entry.setGroupTitle(groupTitle);
        entry.setTvgId(tvgId);
        entry.setTvgName(tvgName);
        entry.setTvgLogo(tvgLogo);
        entry.setTvgShift(tvgShift);
        entry.setRadio(radio);
        entry.setChannelUri(channelUri);
        entry.setName(channelName);
        entry.setVlcOpts(vlcOpts);
        return entry;
    }
}

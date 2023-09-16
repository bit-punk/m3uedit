package org.bitpunk.m3uedit.builder;

import org.bitpunk.m3uedit.model.Entry;

public class Builder {
    private String duration;
    private String groupTitle;
    private String tvgId;
    private String tvgName;
    private String tvgLogo;
    private String tvgShift;
    private String radio;
    private String channelUri;
    private String channelName;

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
        entry.setChannelName(channelName);
        return entry;
    }
}

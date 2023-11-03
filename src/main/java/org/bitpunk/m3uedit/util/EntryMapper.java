package org.bitpunk.m3uedit.util;

import org.bitpunk.m3uedit.model.Entry;

import java.util.HashMap;

public class EntryMapper {
    private EntryMapper() {
    }

    public static void mapEntry(Entry sourceEntry, Entry targetEntry) {
        targetEntry.setDuration(sourceEntry.getDuration());
        targetEntry.setGroupTitle(sourceEntry.getGroupTitle());
        targetEntry.setTvgId(sourceEntry.getTvgId());
        targetEntry.setTvgName(sourceEntry.getTvgName());
        targetEntry.setTvgLogo(sourceEntry.getTvgLogo());
        targetEntry.setTvgShift(sourceEntry.getTvgShift());
        targetEntry.setRadio(sourceEntry.getRadio());
        targetEntry.setChannelUri(sourceEntry.getChannelUri());
        targetEntry.setName(sourceEntry.getName());
        targetEntry.setVlcOpts(new HashMap<>(sourceEntry.getVlcOpts()));
    }

}
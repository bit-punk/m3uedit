package org.bitpunk.m3uedit.util;

import org.bitpunk.m3uedit.model.Entry;

import java.util.HashMap;
import java.util.Map;

public class EntryMapper {
    private EntryMapper() {
    }

    public static void mapEntry(Entry sourceEntry, Entry targetEntry) {
        targetEntry.setIndex(sourceEntry.getIndex());
        targetEntry.setDuration(sourceEntry.getDuration());
        targetEntry.setGroupTitle(sourceEntry.getGroupTitle());
        targetEntry.setTvgId(sourceEntry.getTvgId());
        targetEntry.setTvgName(sourceEntry.getTvgName());
        targetEntry.setTvgLogo(sourceEntry.getTvgLogo());
        targetEntry.setTvgShift(sourceEntry.getTvgShift());
        targetEntry.setRadio(sourceEntry.getRadio());
        targetEntry.setChannelUri(sourceEntry.getChannelUri());
        targetEntry.setName(sourceEntry.getName());
        Map<String, String> sourceVlcOpts = sourceEntry.getVlcOpts();
        if (sourceVlcOpts != null) {
            targetEntry.setVlcOpts(new HashMap<>(sourceVlcOpts));
        }
    }

}
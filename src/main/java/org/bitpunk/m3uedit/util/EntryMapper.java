package org.bitpunk.m3uedit.util;

import org.bitpunk.m3uedit.model.Entry;

import java.util.HashMap;
import java.util.Map;

public class EntryMapper {
    private EntryMapper() {
    }

    /**
     * @return a new entry with the values of the given one
     */
    public static Entry copyOf(Entry entry) {
        Entry copy = new Entry();
        mapEntry(entry, copy);
        return copy;
    }

    /**
     * Describes the attributes changed between two versions of an entry, e.g. {@code tvg-id: ZDF.de}.
     *
     * @return the changed attributes with their new values, comma-separated; empty if nothing changed
     */
    public static String describeChanges(Entry before, Entry after) {
        java.util.List<String> changes = new java.util.ArrayList<>();
        addChange(changes, "name", before.getName(), after.getName());
        addChange(changes, "url", before.getChannelUri(), after.getChannelUri());
        addChange(changes, "tvg-id", before.getTvgId(), after.getTvgId());
        addChange(changes, "tvg-name", before.getTvgName(), after.getTvgName());
        addChange(changes, "tvg-logo", before.getTvgLogo(), after.getTvgLogo());
        addChange(changes, "tvg-shift", before.getTvgShift(), after.getTvgShift());
        addChange(changes, "group-title", before.getGroupTitle(), after.getGroupTitle());
        addChange(changes, "radio", before.getRadio(), after.getRadio());
        addChange(changes, "duration", before.getDuration(), after.getDuration());
        if (!java.util.Objects.equals(emptyIfNull(before.getVlcOpts()), emptyIfNull(after.getVlcOpts()))) {
            changes.add("vlc-options");
        }
        return String.join(", ", changes);
    }

    private static void addChange(java.util.List<String> changes, String attribute, String before, String after) {
        if (!java.util.Objects.equals(before, after)) {
            changes.add("%s: %s".formatted(attribute, after == null ? "removed" : after));
        }
    }

    private static Map<String, String> emptyIfNull(Map<String, String> map) {
        return map == null ? Map.of() : map;
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
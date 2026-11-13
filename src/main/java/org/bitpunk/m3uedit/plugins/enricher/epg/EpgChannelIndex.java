package org.bitpunk.m3uedit.plugins.enricher.epg;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The channels of all EPG sources, to look them up by id or name. If several sources contain a channel (or a name),
 * the first source wins: the sources are given in the order of their priority.
 */
final class EpgChannelIndex {
    private final Map<String, XmltvChannel> byId = new LinkedHashMap<>();
    private final Map<String, Integer> sourceById = new HashMap<>();
    private final Map<String, String> idByLowerCaseId = new HashMap<>();
    private final Map<String, String> idByName = new HashMap<>();

    /**
     * @param channelsPerSource the channels of each source, in the order of the sources
     */
    EpgChannelIndex(List<List<XmltvChannel>> channelsPerSource) {
        for (int source = 0; source < channelsPerSource.size(); source++) {
            for (XmltvChannel channel : channelsPerSource.get(source)) {
                if (byId.putIfAbsent(channel.id(), channel) != null) {
                    continue;
                }
                sourceById.put(channel.id(), source);
                idByLowerCaseId.putIfAbsent(channel.id().toLowerCase(Locale.ROOT), channel.id());
            }
        }
        // names are indexed after all ids: a display name of one source must not hide an id of another one
        for (XmltvChannel channel : byId.values()) {
            for (String displayName : channel.displayNames()) {
                for (String alternative : ChannelNames.alternatives(displayName)) {
                    String name = ChannelNames.normalize(alternative);
                    if (!name.isEmpty()) {
                        idByName.putIfAbsent(name, channel.id());
                    }
                }
            }
        }
    }

    int size() {
        return byId.size();
    }

    /**
     * @return the index of the source the channel with the given id is taken from, -1 if there is none
     */
    int sourceOf(String id) {
        return id == null ? -1 : sourceById.getOrDefault(id, -1);
    }

    boolean contains(String id) {
        return id != null && byId.containsKey(id);
    }

    /**
     * @return the id as written in the sources, ignoring the case; null if there is no channel with this id
     */
    String findId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String strippedId = id.strip();
        return byId.containsKey(strippedId) ? strippedId : idByLowerCaseId.get(strippedId.toLowerCase(Locale.ROOT));
    }

    /**
     * @return the id of the channel with the given (normalized) display name, null if there is none
     */
    String findIdByName(String name) {
        String normalized = ChannelNames.normalize(name);
        return normalized.isEmpty() ? null : idByName.get(normalized);
    }

    /**
     * @return the id of the channel named like the longest prefix of the given name (made of its first words),
     * null if there is none
     */
    String findIdByNamePrefix(String name) {
        for (String prefix : ChannelNames.prefixes(name)) {
            String id = idByName.get(prefix);
            if (id != null) {
                return id;
            }
        }
        return null;
    }
}

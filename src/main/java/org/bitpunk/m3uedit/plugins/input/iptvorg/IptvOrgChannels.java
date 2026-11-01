package org.bitpunk.m3uedit.plugins.input.iptvorg;

import org.bitpunk.m3uedit.model.Entry;

import java.util.regex.Pattern;

/**
 * Derives the iptv-org channel of an {@link Entry} read from an iptv-org playlist.
 */
final class IptvOrgChannels {
    /**
     * Matches the suffixes iptv-org appends to the channel-name of a stream, e.g. " (720p) [Geo-blocked]".
     */
    private static final Pattern STREAM_NAME_SUFFIX = Pattern.compile("(\\s+(\\(\\d+[pi]\\)|\\[[^]]*]))+$");

    private IptvOrgChannels() {
    }

    /**
     * Returns the channel-id of an entry, which is the part of its tvg-id in front of the feed (e.g. "3sat.de" for
     * "3sat.de@SD"). Falls back to the channel-name, if the entry has no tvg-id.
     */
    static String channelIdOf(Entry entry) {
        String tvgId = entry.getTvgId();
        if (tvgId == null || tvgId.isBlank()) {
            return channelNameOf(entry);
        }
        int feedSeparator = tvgId.indexOf('@');
        return feedSeparator < 0 ? tvgId : tvgId.substring(0, feedSeparator);
    }

    /**
     * Returns the name of the channel of an entry, without quality- and label-suffixes (e.g. "3sat" for "3sat (720p)").
     */
    static String channelNameOf(Entry entry) {
        return STREAM_NAME_SUFFIX.matcher(entry.getName()).replaceFirst("");
    }
}

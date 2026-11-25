package org.bitpunk.m3uedit.plugins.enricher.epg;

import java.util.List;

/**
 * A channel of an XMLTV file.
 *
 * @param id           the id the programmes reference the channel by (and the tvg-id of the playlist)
 * @param displayNames the names of the channel, the first one is the preferred one
 */
record XmltvChannel(String id, List<String> displayNames) {
    XmltvChannel {
        displayNames = List.copyOf(displayNames);
    }
}

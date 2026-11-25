package org.bitpunk.m3uedit.api;

import java.time.Instant;

/**
 * A programme of a channel of the playlist, from a programme guide.
 *
 * @param channelId   the tvg-id of the channel in the playlist
 * @param channelName the name of the (first) entry of the playlist with this tvg-id
 * @param start       the start, null if unknown
 * @param stop        the end, null if unknown
 * @param title       the title, null if none
 * @param subTitle    e.g. the title of the episode, null if none
 * @param description null if none
 * @param category    the (first) category, null if none
 * @param icon        the url of an image of the programme, null if none
 */
public record Programme(String channelId, String channelName, Instant start, Instant stop, String title,
                        String subTitle, String description, String category, String icon) {

    public Programme(String channelId, String channelName, Instant start, Instant stop, String title,
                     String subTitle, String description, String category) {
        this(channelId, channelName, start, stop, title, subTitle, description, category, null);
    }
}

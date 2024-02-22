package org.bitpunk.m3uedit.plugins.input.fritzbox.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Represents a source of a tv-streams to be read from the FritzBox ("tvsd.m3u" for SD-Streams, or "tvhd.m3u" for HD-streams).
 * It contains the name and description of the stream source.
 */
@Data
@RequiredArgsConstructor
public class StreamSource {
    private final String name;
    private final String description;
}
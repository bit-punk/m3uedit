package org.bitpunk.m3uedit.plugins.input.fritzbox.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a source of a tv-streams to be read from the FritzBox ("tvsd.m3u" for SD-Streams, or "tvhd.m3u" for HD-streams).
 * It contains the name and description of the stream source.
 */
@Data
public class StreamSource {
    private final String name;
    private final String description;

    @JsonCreator
    public StreamSource(@JsonProperty("name") String name, @JsonProperty("description") String description) {
        this.name = name;
        this.description = description;
    }
}

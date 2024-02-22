package org.bitpunk.m3uedit.plugins.input.fritzbox.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static org.bitpunk.m3uedit.model.Entry.REGEX_URL;


/**
 * Represents a connection to a FritzBox device.
 */
@Data
public class FritzBoxConnection {
    @NotEmpty(message = "baseUrl must not be empty")
    @Pattern(regexp = REGEX_URL, message = "baseUrl must be a valid URL")
    private String baseUrl;
    private List<StreamSource> selectedStreamSources = new ArrayList<>();

    public String toString(){
        String concatenatedStreamNames = selectedStreamSources.stream().map(StreamSource::getName).collect(joining(", "));
        return "base-url: %s, streams: %s".formatted(baseUrl, concatenatedStreamNames);
    }
}
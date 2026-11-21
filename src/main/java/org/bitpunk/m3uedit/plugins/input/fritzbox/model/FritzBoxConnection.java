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
    /**
     * The messages of the validation, translated when they are shown.
     */
    public static final String BASE_URL_EMPTY = "baseUrl must not be empty";
    public static final String BASE_URL_INVALID = "baseUrl must be a valid URL";
    @NotEmpty(message = BASE_URL_EMPTY)
    @Pattern(regexp = REGEX_URL, message = BASE_URL_INVALID)
    private String baseUrl;
    private List<StreamSource> selectedStreamSources = new ArrayList<>();

    public String toString(){
        String concatenatedStreamNames = selectedStreamSources.stream().map(StreamSource::getName).collect(joining(", "));
        return "base-url: %s, streams: %s".formatted(baseUrl, concatenatedStreamNames);
    }
}
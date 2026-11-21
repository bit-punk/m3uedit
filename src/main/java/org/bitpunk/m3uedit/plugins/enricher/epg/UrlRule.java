package org.bitpunk.m3uedit.plugins.enricher.epg;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bitpunk.m3uedit.i18n.Messages;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Assigns the channel id of a programme guide to all entries whose stream url matches a regular expression,
 * e.g. {@code service=514 => ZDF.de}. The id may reference groups of the expression, e.g. {@code $1}.
 *
 * @param regex     a regular expression found somewhere in the stream url
 * @param channelId the id of the channel in the programme guide
 */
public record UrlRule(@JsonProperty("regex") String regex, @JsonProperty("channelId") String channelId) {
    static final String SEPARATOR = "=>";

    public UrlRule {
        Objects.requireNonNull(regex, "regex");
        Objects.requireNonNull(channelId, "channelId");
    }

    /**
     * @return the channel id for the given url, null if the url does not match (or the expression is invalid)
     */
    String channelIdFor(String url) {
        if (url == null) {
            return null;
        }
        try {
            Matcher matcher = pattern().matcher(url);
            if (!matcher.find()) {
                return null;
            }
            StringBuilder id = new StringBuilder();
            matcher.appendReplacement(id, channelId);
            // appendReplacement also copies the text before the match, only the replacement is wanted
            String replaced = id.substring(matcher.start());
            return replaced.isBlank() ? null : replaced.strip();
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    @JsonIgnore
    Pattern pattern() {
        return Pattern.compile(regex);
    }

    /**
     * Parses a rule written as {@code regex => channel-id}.
     *
     * @throws IllegalArgumentException if the line is no valid rule
     */
    static UrlRule parse(String line) {
        int separator = line.lastIndexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException(Messages.get("epgEnricher.urlRule.separatorMissing", SEPARATOR, line));
        }
        String regex = line.substring(0, separator).strip();
        String channelId = line.substring(separator + SEPARATOR.length()).strip();
        if (regex.isEmpty() || channelId.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("epgEnricher.urlRule.incomplete", line));
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(Messages.get("epgEnricher.urlRule.invalidExpression", regex,
                    e.getDescription()));
        }
        return new UrlRule(regex, channelId);
    }

    /**
     * @return the rule written as {@code regex => channel-id}
     */
    String format() {
        return regex + " " + SEPARATOR + " " + channelId;
    }
}

package org.bitpunk.m3uedit.plugins.enricher.epg;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the times of XMLTV programmes, e.g. {@code 20261113201500 +0100}. Seconds, minutes and hours may be
 * missing; a time without offset is taken as local time.
 */
final class XmltvTimes {
    private static final Pattern TIME = Pattern.compile(
            "^(\\d{4})(\\d{2})(\\d{2})(\\d{2})?(\\d{2})?(\\d{2})?\\s*([+-]\\d{2}:?\\d{2}|Z|UTC|GMT)?");

    private XmltvTimes() {
    }

    /**
     * @return the instant of the given time, null if it cannot be parsed
     */
    static Instant parse(String time, ZoneId defaultZone) {
        if (time == null) {
            return null;
        }
        Matcher matcher = TIME.matcher(time.strip());
        if (!matcher.find()) {
            return null;
        }
        try {
            LocalDateTime local = LocalDateTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), number(matcher.group(4)), number(matcher.group(5)),
                    number(matcher.group(6)));
            String offset = matcher.group(7);
            if (offset == null) {
                return local.atZone(defaultZone).toInstant();
            }
            if (offset.equals("Z") || offset.equals("UTC") || offset.equals("GMT")) {
                return local.toInstant(ZoneOffset.UTC);
            }
            return local.toInstant(ZoneOffset.of(offset.length() == 5
                    ? offset.substring(0, 3) + ":" + offset.substring(3) : offset));
        } catch (java.time.DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static int number(String digits) {
        return digits == null ? 0 : Integer.parseInt(digits);
    }
}

package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class XmltvTimesTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void testTimesAreParsed() {
        assertThat(XmltvTimes.parse("20261113201500 +0100", BERLIN)).isEqualTo(Instant.parse("2026-11-13T19:15:00Z"));
        assertThat(XmltvTimes.parse("20261113201500 -05:00", BERLIN)).isEqualTo(Instant.parse("2026-11-14T01:15:00Z"));
        assertThat(XmltvTimes.parse("20261113201500Z", BERLIN)).isEqualTo(Instant.parse("2026-11-13T20:15:00Z"));
        assertThat(XmltvTimes.parse("202611132015 +0000", BERLIN)).isEqualTo(Instant.parse("2026-11-13T20:15:00Z"));
        assertThat(XmltvTimes.parse("20261113", BERLIN)).as("local time").isEqualTo(Instant.parse("2026-11-12T23:00:00Z"));
    }

    @Test
    void testInvalidTimesAreNull() {
        assertThat(XmltvTimes.parse(null, BERLIN)).isNull();
        assertThat(XmltvTimes.parse("tomorrow", BERLIN)).isNull();
        assertThat(XmltvTimes.parse("20261399000000 +0100", BERLIN)).isNull();
        assertThat(XmltvTimes.parse("20261113000000 +2500", BERLIN)).isNull();
    }
}

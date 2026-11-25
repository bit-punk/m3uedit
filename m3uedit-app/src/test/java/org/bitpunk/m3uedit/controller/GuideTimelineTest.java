package org.bitpunk.m3uedit.controller;

import org.bitpunk.m3uedit.controller.GuideTimeline.Slot;
import org.bitpunk.m3uedit.api.Programme;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTimelineTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static Programme programme(String title, String start, String stop) {
        return new Programme("a", "A", start == null ? null : Instant.parse(start), stop == null ? null : Instant.parse(stop),
                title, null, null, null);
    }

    @Test
    void testProgrammesWithoutEndLastUntilTheNextOne() {
        Programme first = programme("first", "2026-11-13T18:00:00Z", null);
        Programme second = programme("second", "2026-11-13T18:20:00Z", "2026-11-13T19:30:00Z");
        Programme overlapping = programme("overlapping", "2026-11-13T19:00:00Z", "2026-11-13T19:45:00Z");
        Programme last = programme("last", "2026-11-13T20:00:00Z", null);
        Programme withoutStart = programme("without start", null, null);

        List<Slot> slots = GuideTimeline.slots(List.of(last, overlapping, withoutStart, second, first));

        assertThat(slots).extracting(slot -> slot.programme().title()).containsExactly("first", "second", "overlapping", "last");
        assertThat(slots.get(0).end()).isEqualTo(Instant.parse("2026-11-13T18:20:00Z"));
        assertThat(slots.get(1).end()).as("cut at the next one").isEqualTo(Instant.parse("2026-11-13T19:00:00Z"));
        assertThat(slots.get(3).end()).isEqualTo(Instant.parse("2026-11-13T20:30:00Z"));
    }

    @Test
    void testTimelineStartsAtTheFullHourAndMapsTimesToPositions() {
        List<Slot> row = GuideTimeline.slots(List.of(programme("a", "2026-11-13T18:20:00Z", "2026-11-13T19:10:00Z")));

        GuideTimeline timeline = GuideTimeline.covering(List.of(row), BERLIN, 2);

        assertThat(timeline.start()).isEqualTo(Instant.parse("2026-11-13T18:00:00Z"));
        assertThat(timeline.end()).isEqualTo(Instant.parse("2026-11-13T20:00:00Z"));
        assertThat(timeline.x(Instant.parse("2026-11-13T18:30:00Z"))).isEqualTo(60.0);
        assertThat(timeline.timeAt(60)).isEqualTo(Instant.parse("2026-11-13T18:30:00Z"));
        assertThat(timeline.width()).isEqualTo(240.0);
        assertThat(timeline.ticks()).hasSize(5).first().isEqualTo(timeline.start());
    }

    @Test
    void testSlotAtTimeIsTheRunningOneOrTheNext() {
        List<Slot> row = GuideTimeline.slots(List.of(
                programme("a", "2026-11-13T18:00:00Z", "2026-11-13T18:30:00Z"),
                programme("b", "2026-11-13T19:00:00Z", "2026-11-13T19:30:00Z")));

        assertThat(GuideTimeline.slotAt(row, Instant.parse("2026-11-13T18:10:00Z")).programme().title()).isEqualTo("a");
        assertThat(GuideTimeline.slotAt(row, Instant.parse("2026-11-13T18:30:00Z")).programme().title()).as("gap").isEqualTo("b");
        assertThat(GuideTimeline.slotAt(row, Instant.parse("2026-11-13T21:00:00Z")).programme().title()).isEqualTo("b");
        assertThat(GuideTimeline.slotAt(List.of(), Instant.parse("2026-11-13T21:00:00Z"))).isNull();
    }
}

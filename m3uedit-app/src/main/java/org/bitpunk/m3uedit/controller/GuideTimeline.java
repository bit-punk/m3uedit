package org.bitpunk.m3uedit.controller;

import org.bitpunk.m3uedit.api.Programme;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The time axis of the programme guide: maps times to horizontal positions, and computes the time span of each
 * programme shown.
 *
 * @param start        the time at position 0, a full hour
 * @param end          the time at the right end
 * @param pixelsPerMinute the width of a minute
 */
record GuideTimeline(Instant start, Instant end, double pixelsPerMinute) {
    /**
     * Assumed duration of the last programme of a channel if it has no end.
     */
    static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);

    /**
     * A programme with the time span it is shown with.
     */
    record Slot(Programme programme, Instant start, Instant end) {
        boolean isRunningAt(Instant time) {
            return !start.isAfter(time) && end.isAfter(time);
        }
    }

    /**
     * @return the timeline covering all programmes, starting at the full hour before the first one
     */
    static GuideTimeline covering(List<List<Slot>> rows, ZoneId zone, double pixelsPerMinute) {
        Instant first = rows.stream().flatMap(List::stream).map(Slot::start).min(Comparator.naturalOrder())
                .orElse(Instant.now());
        Instant last = rows.stream().flatMap(List::stream).map(Slot::end).max(Comparator.naturalOrder())
                .orElse(first.plus(Duration.ofHours(1)));
        Instant start = first.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant();
        Instant end = last.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
        return new GuideTimeline(start, end, pixelsPerMinute);
    }

    /**
     * Computes the time span each programme of a channel is shown with: a programme without end lasts until the
     * next one starts; programmes without start are left out, overlapping ones are cut at the start of the next one.
     *
     * @param programmes the programmes of one channel
     */
    static List<Slot> slots(List<Programme> programmes) {
        List<Programme> sorted = programmes.stream().filter(programme -> programme.start() != null)
                .sorted(Comparator.comparing(Programme::start)).toList();
        List<Slot> slots = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            Programme programme = sorted.get(i);
            Instant next = i + 1 < sorted.size() ? sorted.get(i + 1).start() : null;
            Instant end = programme.stop() != null ? programme.stop()
                    : Objects.requireNonNullElse(next, programme.start().plus(DEFAULT_DURATION));
            if (next != null && end.isAfter(next)) {
                end = next;
            }
            if (end.isAfter(programme.start())) {
                slots.add(new Slot(programme, programme.start(), end));
            }
        }
        return slots;
    }

    double x(Instant time) {
        return Duration.between(start, time).toSeconds() / 60.0 * pixelsPerMinute;
    }

    Instant timeAt(double x) {
        return start.plusSeconds(Math.round(x / pixelsPerMinute * 60));
    }

    double width() {
        return x(end);
    }

    /**
     * @return the full and half hours within the timeline
     */
    List<Instant> ticks() {
        List<Instant> ticks = new ArrayList<>();
        for (Instant tick = start; !tick.isAfter(end); tick = tick.plus(Duration.ofMinutes(30))) {
            ticks.add(tick);
        }
        return ticks;
    }

    /**
     * @return the slot of the row running at the given time, or the slot starting next after it, or the last one;
     * null if the row is empty
     */
    static Slot slotAt(List<Slot> row, Instant time) {
        Slot next = null;
        for (Slot slot : row) {
            if (slot.isRunningAt(time)) {
                return slot;
            }
            if (next == null && slot.start().isAfter(time)) {
                next = slot;
            }
        }
        return next != null ? next : row.isEmpty() ? null : row.getLast();
    }
}

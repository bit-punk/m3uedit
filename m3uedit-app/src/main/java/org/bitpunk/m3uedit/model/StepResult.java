package org.bitpunk.m3uedit.model;

import org.bitpunk.m3uedit.api.Entry;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effect of a single enabled filter, sorter or enricher: the entries it received and the entries it passed on.
 *
 * @param input         the entries before this step
 * @param output        the entries after this step
 * @param movedCount    the number of entries whose relative order has been changed by this step
 * @param modifiedCount the number of entries changed by this step (enrichers only)
 * @param notes         a short note for entries of the output worth mentioning (enrichers only)
 * @param summary       a short summary of the result, null if none (enrichers only)
 */
public record StepResult(List<Entry> input, List<Entry> output, int movedCount, int modifiedCount,
                         Map<Entry, String> notes, String summary) {

    public StepResult {
        input = List.copyOf(input);
        output = List.copyOf(output);
        notes = notes == null ? Map.of() : Collections.unmodifiableMap(new IdentityHashMap<>(notes));
    }

    public StepResult(List<Entry> input, List<Entry> output, int movedCount) {
        this(input, output, movedCount, 0, null, null);
    }

    /**
     * Creates the result of an enricher, which keeps the order of the entries: each entry of the output is the
     * entry of the input at the same position, or a changed copy of it.
     */
    public static StepResult ofEnrichment(List<Entry> input, List<Entry> output, Map<Entry, String> notes,
                                          String summary) {
        int modified = 0;
        for (int i = 0; i < output.size(); i++) {
            if (output.get(i) != input.get(i)) {
                modified++;
            }
        }
        return new StepResult(input, output, 0, modified, notes, summary);
    }

    public static StepResult ofEnrichment(List<Entry> input, List<Entry> output) {
        return ofEnrichment(input, output, null, null);
    }

    /**
     * Creates the result of a step, counting the moved entries.
     */
    public static StepResult of(List<Entry> input, List<Entry> output) {
        return new StepResult(input, output, EntryDiff.compute(input, output).getMovedCount());
    }

    /**
     * @return the number of entries removed by this step
     */
    public int removedCount() {
        return input.size() - output.size();
    }
}

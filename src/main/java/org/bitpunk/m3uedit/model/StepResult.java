package org.bitpunk.m3uedit.model;

import java.util.List;

/**
 * The effect of a single enabled filter, sorter or enricher: the entries it received and the entries it passed on.
 *
 * @param input         the entries before this step
 * @param output        the entries after this step
 * @param movedCount    the number of entries whose relative order has been changed by this step
 * @param modifiedCount the number of entries changed by this step (enrichers only)
 */
public record StepResult(List<Entry> input, List<Entry> output, int movedCount, int modifiedCount) {

    public StepResult {
        input = List.copyOf(input);
        output = List.copyOf(output);
    }

    public StepResult(List<Entry> input, List<Entry> output, int movedCount) {
        this(input, output, movedCount, 0);
    }

    /**
     * Creates the result of an enricher, which keeps the order of the entries: each entry of the output is the
     * entry of the input at the same position, or a changed copy of it.
     */
    public static StepResult ofEnrichment(List<Entry> input, List<Entry> output) {
        int modified = 0;
        for (int i = 0; i < output.size(); i++) {
            if (output.get(i) != input.get(i)) {
                modified++;
            }
        }
        return new StepResult(input, output, 0, modified);
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

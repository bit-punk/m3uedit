package org.bitpunk.m3uedit.model;

import java.util.List;

/**
 * The effect of a single enabled filter or sorter: the entries it received and the entries it passed on.
 *
 * @param input      the entries before this step
 * @param output     the entries after this step
 * @param movedCount the number of entries whose relative order has been changed by this step
 */
public record StepResult(List<Entry> input, List<Entry> output, int movedCount) {

    public StepResult {
        input = List.copyOf(input);
        output = List.copyOf(output);
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

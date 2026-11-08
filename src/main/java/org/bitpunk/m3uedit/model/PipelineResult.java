package org.bitpunk.m3uedit.model;

import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The intermediate results of the last computation of a project: inputs &rarr; filters &rarr; sorters.
 * <p>
 * Entries and plugins are compared by identity, so all maps are identity-maps.
 *
 * @param raw       the entries read from all inputs
 * @param filtered  the entries after all enabled filters
 * @param sorted    the entries after all enabled sorters, as computed (the playlist may be edited afterwards)
 * @param sources   the input each raw entry has been read from
 * @param steps     the result of each enabled filter and sorter, disabled plugins have no result
 * @param removedBy the filter that removed an entry, for each entry removed by a filter
 */
public record PipelineResult(List<Entry> raw, List<Entry> filtered, List<Entry> sorted,
                             Map<Entry, Input> sources, Map<Plugin, StepResult> steps,
                             Map<Entry, Filter> removedBy) {

    public static final PipelineResult EMPTY = new PipelineResult(List.of(), List.of(), List.of(),
            new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());

    public PipelineResult {
        raw = List.copyOf(raw);
        filtered = List.copyOf(filtered);
        sorted = List.copyOf(sorted);
        sources = Collections.unmodifiableMap(new IdentityHashMap<>(sources));
        steps = Collections.unmodifiableMap(new IdentityHashMap<>(steps));
        removedBy = Collections.unmodifiableMap(new IdentityHashMap<>(removedBy));
    }

    /**
     * @return the result of the given filter or sorter, empty if it was disabled
     */
    public Optional<StepResult> step(Plugin plugin) {
        return Optional.ofNullable(steps.get(plugin));
    }

    /**
     * @return the raw entries read from the given input, in their order
     */
    public List<Entry> entriesOf(Input input) {
        return raw.stream().filter(entry -> sources.get(entry) == input).toList();
    }

    /**
     * @return a short description why the given entry has been removed, null if it has not been removed by a filter
     */
    public String removalReason(Entry entry) {
        Filter filter = removedBy.get(entry);
        if (filter == null) {
            return null;
        }
        String reason = step(filter).map(step -> filter.describeRemoval(entry, step.input())).orElse(null);
        return reason != null ? "%s: %s".formatted(filter.getName(), reason) : filter.getName();
    }
}

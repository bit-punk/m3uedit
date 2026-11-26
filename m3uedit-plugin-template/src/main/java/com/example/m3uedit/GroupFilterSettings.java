package com.example.m3uedit;

import java.util.List;

/**
 * The settings of the {@link GroupFilter}, stored in the project-file of m3uedit: a record of plain Java types.
 * <p>
 * A project-file written by an older version of the plugin may lack components: they are {@code null} then, so they
 * are objects ({@code Boolean}, not {@code boolean}) and the plugin replaces them by its defaults.
 *
 * @param groups     the groups (group-title of the entries), compared ignoring case
 * @param keepGroups true to keep only the entries of the groups, false to remove them
 */
public record GroupFilterSettings(List<String> groups, Boolean keepGroups) {

    /**
     * Replaces missing components by the defaults: no groups, removing them (so a new filter removes nothing).
     */
    public GroupFilterSettings {
        groups = groups == null ? List.of() : List.copyOf(groups);
        keepGroups = keepGroups != null && keepGroups;
    }

    /**
     * @return the settings of a new filter
     */
    public static GroupFilterSettings defaults() {
        return new GroupFilterSettings(null, null);
    }
}

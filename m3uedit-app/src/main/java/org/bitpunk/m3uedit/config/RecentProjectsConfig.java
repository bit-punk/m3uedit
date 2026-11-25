package org.bitpunk.m3uedit.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The recently used project-files, stored in {@code ~/.m3uedit/recent_projects.yaml}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentProjectsConfig {
    public static final int DEFAULT_MAX_ENTRIES = 10;

    /**
     * How many project-files are remembered and shown, 0 turns the list off.
     */
    private int maxEntries = DEFAULT_MAX_ENTRIES;
    /**
     * The project-files, the most recently used first.
     */
    private List<RecentProject> projects = new ArrayList<>();
}

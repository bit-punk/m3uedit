package org.bitpunk.m3uedit.config;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The recently used project-files, the most recently used first. Kept in {@code ~/.m3uedit/recent_projects.yaml},
 * where the number of files remembered can be changed ({@code maxEntries}, 10 by default).
 * <p>
 * The file is read again before each change, so the list of another running instance is not overwritten, and a
 * changed {@code maxEntries} takes effect without restart.
 */
public class RecentProjects {
    public static final String CONFIG_FILE_NAME = "recent_projects.yaml";

    private final YamlConfigManager<RecentProjectsConfig> configManager;
    private final Clock clock;
    private final ObservableList<RecentProject> projects = FXCollections.observableArrayList();
    private final ObservableList<RecentProject> unmodifiableProjects = FXCollections.unmodifiableObservableList(projects);
    @Getter
    private int maxEntries = RecentProjectsConfig.DEFAULT_MAX_ENTRIES;

    public RecentProjects() {
        this(new YamlConfigManager<>(AppConfigPaths.getAppConfigPath(CONFIG_FILE_NAME), RecentProjectsConfig.class,
                RecentProjectsConfig::new), Clock.systemDefaultZone());
    }

    public RecentProjects(YamlConfigManager<RecentProjectsConfig> configManager, Clock clock) {
        this.configManager = configManager;
        this.clock = clock;
        reload();
    }

    /**
     * @return the project-files, the most recently used first, at most {@link #getMaxEntries()}
     */
    public ObservableList<RecentProject> getProjects() {
        return unmodifiableProjects;
    }

    /**
     * Reads the list from its file.
     */
    public void reload() {
        RecentProjectsConfig config = configManager.loadConfig();
        maxEntries = Math.max(0, config.getMaxEntries());
        List<RecentProject> read = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        for (RecentProject project : Objects.requireNonNullElse(config.getProjects(), List.<RecentProject>of())) {
            if (project != null && project.path() != null && !project.path().isBlank()
                    && seen.add(normalize(project.file())) && read.size() < maxEntries) {
                read.add(project);
            }
        }
        projects.setAll(read);
    }

    /**
     * Puts the given project-file at the top of the list, as opened or saved now.
     */
    public void add(Path projectFile) {
        reload();
        Path file = normalize(projectFile);
        List<RecentProject> changed = new ArrayList<>(projects);
        changed.removeIf(project -> normalize(project.file()).equals(file));
        changed.addFirst(new RecentProject(file.toString(), clock.instant()));
        save(changed);
    }

    /**
     * Removes the given project-file from the list, e.g. because it no longer exists.
     */
    public void remove(Path projectFile) {
        reload();
        Path file = normalize(projectFile);
        List<RecentProject> changed = new ArrayList<>(projects);
        if (changed.removeIf(project -> normalize(project.file()).equals(file))) {
            save(changed);
        }
    }

    /**
     * Changes how many project-files are remembered; the oldest ones are dropped if there are more.
     */
    public void setMaxEntries(int maxEntries) {
        reload();
        this.maxEntries = Math.max(0, maxEntries);
        save(new ArrayList<>(projects));
    }

    public void clear() {
        reload();
        save(List.of());
    }

    private void save(List<RecentProject> changed) {
        List<RecentProject> kept = changed.subList(0, Math.min(changed.size(), maxEntries));
        projects.setAll(kept);
        configManager.saveConfig(new RecentProjectsConfig(maxEntries, new ArrayList<>(kept)));
    }

    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }
}

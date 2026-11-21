package org.bitpunk.m3uedit.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The general settings without a file of their own, stored in {@code ~/.m3uedit/settings.yaml}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettingsConfig {
    /**
     * What is shown when m3uedit is started.
     */
    private StartupMode startup = StartupMode.START_SCREEN;
    /**
     * The project-files open when m3uedit was closed the last time, reopened with
     * {@link StartupMode#REOPEN_PROJECTS}.
     */
    private List<String> openProjects = new ArrayList<>();
}

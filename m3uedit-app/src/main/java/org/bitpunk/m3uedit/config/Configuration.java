package org.bitpunk.m3uedit.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.player.ExternalPlayer;
import org.bitpunk.m3uedit.player.ExternalPlayerConfig;
import org.bitpunk.m3uedit.plugins.EnricherPluginService;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.SorterPluginService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The general settings of m3uedit, edited by the configuration dialog. They are kept in the files they have been
 * kept in before ({@code external_player.yaml}, {@code recent_projects.yaml}), the new ones in
 * {@code settings.yaml}; all of them take effect without restart.
 */
@Slf4j
public class Configuration {

    /**
     * The settings.
     *
     * @param playerCommand     the external player, its name (found via PATH) or its path
     * @param playerArguments   additional arguments of the player
     * @param passVlcOptions    whether the VLC options of the entries are passed to the player
     * @param maxRecentProjects how many recently used projects are remembered
     * @param startup           what is shown at startup
     * @param language          the language of the user interface, used after a restart
     * @param theme             the appearance of the user interface
     */
    public record Values(String playerCommand, List<String> playerArguments, boolean passVlcOptions,
                         int maxRecentProjects, StartupMode startup, Language language, Theme theme) {
        public Values {
            playerArguments = List.copyOf(playerArguments);
        }
    }

    /**
     * The id of the recently used projects among the collected data.
     */
    public static final String RECENT_PROJECTS = "org.bitpunk.m3uedit.recentProjects";

    private final ExternalPlayer player;
    private final RecentProjects recentProjects;
    private final Supplier<List<CollectedData>> pluginData;
    private final Settings settings;
    private List<CollectedData> collectedData;

    /**
     * @param recentProjects the recently used projects shown by the main window, so it shows the changes at once
     */
    public Configuration(RecentProjects recentProjects, Settings settings) {
        this(new ExternalPlayer(), recentProjects, Configuration::dataOfThePlugins, settings);
    }

    /**
     * @param pluginData the data collected by the plugins
     */
    public Configuration(ExternalPlayer player, RecentProjects recentProjects, Supplier<List<CollectedData>> pluginData,
                         Settings settings) {
        this.player = player;
        this.recentProjects = recentProjects;
        this.pluginData = pluginData;
        this.settings = settings;
    }

    /**
     * @return the data collected by all plugins; data shared by several plugins (e.g. the recently used SFTP servers of
     * the SFTP input and output) once
     */
    static List<CollectedData> dataOfThePlugins() {
        Map<String, CollectedData> data = new LinkedHashMap<>();
        Stream.of(InputPluginService.getInstance(), FilterPluginService.getInstance(), SorterPluginService.getInstance(),
                        EnricherPluginService.getInstance(), OutputPluginService.getInstance())
                .<PluginDescriptor<?>>flatMap(service -> service.getDescriptors().stream())
                .map(PluginDescriptor::create)
                .map(Plugin::getCollectedData)
                .flatMap(List::stream)
                .forEach(collected -> data.putIfAbsent(collected.getId(), collected));
        return List.copyOf(data.values());
    }

    public Values load() {
        ExternalPlayerConfig playerConfig = Objects.requireNonNullElseGet(player.loadConfig(), ExternalPlayerConfig::new);
        recentProjects.reload();
        return new Values(StringUtils.defaultIfBlank(playerConfig.getCommand(), ExternalPlayerConfig.DEFAULT_COMMAND),
                Objects.requireNonNullElse(playerConfig.getArguments(), List.of()), playerConfig.isPassVlcOptions(),
                recentProjects.getMaxEntries(), settings.getStartup(), settings.getLanguage(),
                settings.getTheme());
    }

    /**
     * @return the data collected while using m3uedit, which can be cleared: the recently used projects first, then
     * the data of the plugins
     */
    public List<CollectedData> collectedData() {
        if (collectedData == null) {
            List<CollectedData> data = new ArrayList<>();
            data.add(recentProjectsData());
            data.addAll(pluginData.get());
            collectedData = List.copyOf(data);
        }
        return collectedData;
    }

    private CollectedData recentProjectsData() {
        return new CollectedData() {
            @Override
            public String getId() {
                return RECENT_PROJECTS;
            }

            @Override
            public String getName() {
                return Messages.get("configuration.recent.projects");
            }

            @Override
            public String describe() {
                return Messages.get("configuration.collected.entries", recentProjects.getProjects().size());
            }

            @Override
            public boolean isEmpty() {
                return recentProjects.getProjects().isEmpty();
            }

            @Override
            public void clear() {
                recentProjects.clear();
            }
        };
    }

    /**
     * Stores the settings and clears the data given.
     *
     * @param toClear the ids of the collected data to clear
     * @return the problems, e.g. a file of the EPG cache that could not be deleted; empty if there are none
     */
    public List<String> apply(Values values, Set<String> toClear) {
        List<String> problems = new ArrayList<>();
        ExternalPlayerConfig playerConfig = Objects.requireNonNullElseGet(player.loadConfig(), ExternalPlayerConfig::new);
        playerConfig.setCommand(StringUtils.defaultIfBlank(StringUtils.strip(values.playerCommand()),
                ExternalPlayerConfig.DEFAULT_COMMAND));
        playerConfig.setArguments(new ArrayList<>(values.playerArguments()));
        playerConfig.setPassVlcOptions(values.passVlcOptions());
        player.saveConfig(playerConfig);
        if (values.maxRecentProjects() != recentProjects.getMaxEntries()) {
            recentProjects.setMaxEntries(values.maxRecentProjects());
        }
        settings.setStartup(values.startup());
        settings.setLanguage(values.language());
        settings.setTheme(values.theme());

        for (CollectedData data : collectedData()) {
            if (toClear.contains(data.getId())) {
                try {
                    data.clear();
                } catch (IOException | RuntimeException e) {
                    log.warn("could not clear {}", data.getId(), e);
                    problems.add(Messages.get("configuration.problem.clear", data.getName(), e.getMessage()));
                }
            }
        }
        return problems;
    }
}

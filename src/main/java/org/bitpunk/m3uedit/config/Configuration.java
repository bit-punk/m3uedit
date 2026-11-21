package org.bitpunk.m3uedit.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.player.ExternalPlayer;
import org.bitpunk.m3uedit.player.ExternalPlayerConfig;
import org.bitpunk.m3uedit.plugins.enricher.epg.EpgCache;
import org.bitpunk.m3uedit.plugins.input.fritzbox.RecentFritzBoxConnections;
import org.bitpunk.m3uedit.plugins.sftp.RecentSftpServers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
     * Data collected while using m3uedit, which can be cleared.
     */
    public enum Collected {
        RECENT_PROJECTS, SFTP_SERVERS, FRITZBOXES, EPG_CACHE
    }

    private final ExternalPlayer player;
    private final RecentProjects recentProjects;
    private final RecentSftpServers sftpServers;
    private final RecentFritzBoxConnections fritzBoxes;
    private final Path epgCache;
    private final Settings settings;

    /**
     * @param recentProjects the recently used projects shown by the main window, so it shows the changes at once
     */
    public Configuration(RecentProjects recentProjects, Settings settings) {
        this(new ExternalPlayer(), recentProjects, new RecentSftpServers(), new RecentFritzBoxConnections(),
                EpgCache.directory(), settings);
    }

    public Configuration(ExternalPlayer player, RecentProjects recentProjects, RecentSftpServers sftpServers,
                         RecentFritzBoxConnections fritzBoxes, Path epgCache, Settings settings) {
        this.player = player;
        this.recentProjects = recentProjects;
        this.sftpServers = sftpServers;
        this.fritzBoxes = fritzBoxes;
        this.epgCache = epgCache;
        this.settings = settings;
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
     * @return how much has been collected, e.g. "3 entries" or "empty"
     */
    public String describe(Collected collected) {
        return switch (collected) {
            case RECENT_PROJECTS -> entries(recentProjects.getProjects().size());
            case SFTP_SERVERS -> entries(sftpServers.getServers().size());
            case FRITZBOXES -> entries(fritzBoxes.getConnections().size());
            case EPG_CACHE -> EpgCache.usage(epgCache).describe();
        };
    }

    public boolean isEmpty(Collected collected) {
        return switch (collected) {
            case RECENT_PROJECTS -> recentProjects.getProjects().isEmpty();
            case SFTP_SERVERS -> sftpServers.getServers().isEmpty();
            case FRITZBOXES -> fritzBoxes.getConnections().isEmpty();
            case EPG_CACHE -> EpgCache.usage(epgCache).files() == 0;
        };
    }

    private static String entries(int count) {
        return Messages.get("configuration.collected.entries", count);
    }

    /**
     * Stores the settings and clears the data given.
     *
     * @return the problems, e.g. a file of the EPG cache that could not be deleted; empty if there are none
     */
    public List<String> apply(Values values, Set<Collected> toClear) {
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

        if (toClear.contains(Collected.RECENT_PROJECTS)) {
            recentProjects.clear();
        }
        if (toClear.contains(Collected.SFTP_SERVERS)) {
            sftpServers.clear();
        }
        if (toClear.contains(Collected.FRITZBOXES)) {
            fritzBoxes.clear();
        }
        if (toClear.contains(Collected.EPG_CACHE)) {
            try {
                EpgCache.clear(epgCache);
            } catch (IOException e) {
                log.warn("could not clear the EPG cache {}", epgCache, e);
                problems.add(Messages.get("configuration.problem.epgCache", e.getMessage()));
            }
        }
        return problems;
    }
}

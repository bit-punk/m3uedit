package org.bitpunk.m3uedit.config;

import org.bitpunk.m3uedit.config.Configuration.Collected;
import org.bitpunk.m3uedit.player.ExternalPlayer;
import org.bitpunk.m3uedit.player.ExternalPlayerConfig;
import org.bitpunk.m3uedit.plugins.input.fritzbox.RecentFritzBoxConnections;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.sftp.RecentSftpServers;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationTest {

    @TempDir
    Path tempDir;
    private ExternalPlayer player;
    private RecentProjects recentProjects;
    private RecentSftpServers sftpServers;
    private RecentFritzBoxConnections fritzBoxes;
    private Path epgCache;
    private Settings settings;
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        player = new ExternalPlayer(new YamlConfigManager<>(tempDir.resolve("external_player.yaml"),
                "player/external_player.yaml", ExternalPlayerConfig.class, ExternalPlayerConfig::new));
        recentProjects = new RecentProjects(new YamlConfigManager<>(tempDir.resolve("recent_projects.yaml"),
                RecentProjectsConfig.class, RecentProjectsConfig::new), Clock.systemUTC());
        sftpServers = new RecentSftpServers(tempDir.resolve("sftp.yaml"));
        fritzBoxes = new RecentFritzBoxConnections(tempDir.resolve("fritzbox.yaml"));
        epgCache = Files.createDirectories(tempDir.resolve("cache").resolve("epg"));
        settings = new Settings(tempDir.resolve("settings.yaml"));
        configuration = new Configuration(player, recentProjects, sftpServers, fritzBoxes, epgCache, settings);
    }

    private Path project(String name) throws Exception {
        return Files.writeString(tempDir.resolve(name + ".m3uproj"), "name: " + name);
    }

    private void collectSomething() throws Exception {
        recentProjects.add(project("tv"));
        SftpConnection sftp = new SftpConnection();
        sftp.setHostName("192.168.178.22");
        sftp.setPort(22);
        sftp.setUserName("root");
        sftp.setPath("/storage");
        sftp.setFileName("tv.m3u");
        sftpServers.recordSuccess(sftp);
        FritzBoxConnection fritzBox = new FritzBoxConnection();
        fritzBox.setBaseUrl("http://192.168.178.1");
        fritzBoxes.recordSuccess(fritzBox);
        Files.writeString(epgCache.resolve("guide.xml"), "guide");
    }

    @Test
    void testTheDefaults() {
        Configuration.Values values = configuration.load();

        assertThat(values.playerCommand()).isEqualTo("vlc");
        assertThat(values.passVlcOptions()).isTrue();
        assertThat(values.maxRecentProjects()).isEqualTo(RecentProjectsConfig.DEFAULT_MAX_ENTRIES);
        assertThat(values.startup()).isEqualTo(StartupMode.START_SCREEN);
    }

    @Test
    void testTheSettingsAreStoredInTheirFiles() throws Exception {
        configuration.apply(new Configuration.Values(" /usr/bin/mpv ", List.of("--fs", "--title=My TV"), false, 5,
                StartupMode.REOPEN_PROJECTS, Language.SYSTEM, Theme.DARK), Set.of());

        ExternalPlayerConfig playerConfig = player.loadConfig();
        assertThat(playerConfig.getCommand()).isEqualTo("/usr/bin/mpv");
        assertThat(playerConfig.getArguments()).containsExactly("--fs", "--title=My TV");
        assertThat(playerConfig.isPassVlcOptions()).isFalse();
        assertThat(Files.readString(tempDir.resolve("recent_projects.yaml"))).contains("maxEntries: 5");
        assertThat(settings.getStartup()).isEqualTo(StartupMode.REOPEN_PROJECTS);
        assertThat(settings.getTheme()).isEqualTo(Theme.DARK);
        assertThat(configuration.load()).isEqualTo(new Configuration.Values("/usr/bin/mpv",
                List.of("--fs", "--title=My TV"), false, 5, StartupMode.REOPEN_PROJECTS, Language.SYSTEM, Theme.DARK));
    }

    @Test
    void testWithoutCommandTheDefaultPlayerIsUsed() {
        configuration.apply(new Configuration.Values("  ", List.of(), true, 10, StartupMode.START_SCREEN,
                Language.SYSTEM, Theme.LIGHT), Set.of());

        assertThat(player.loadConfig().getCommand()).isEqualTo("vlc");
    }

    @Test
    void testFewerRecentProjectsDropTheOldestAtOnce() throws Exception {
        recentProjects.add(project("first"));
        recentProjects.add(project("second"));
        recentProjects.add(project("third"));

        configuration.apply(new Configuration.Values("vlc", List.of(), true, 2, StartupMode.START_SCREEN,
                Language.SYSTEM, Theme.LIGHT), Set.of());

        assertThat(recentProjects.getProjects()).extracting(RecentProject::name).containsExactly("third", "second");
    }

    @Test
    void testTheCollectedDataIsDescribedAndCleared() throws Exception {
        collectSomething();
        assertThat(configuration.describe(Collected.RECENT_PROJECTS)).isEqualTo("1 entry");
        assertThat(configuration.describe(Collected.SFTP_SERVERS)).isEqualTo("1 entry");
        assertThat(configuration.describe(Collected.FRITZBOXES)).isEqualTo("1 entry");
        assertThat(configuration.describe(Collected.EPG_CACHE)).isEqualTo("5 bytes in 1 file");

        List<String> problems = configuration.apply(configuration.load(), Set.of(Collected.values()));

        assertThat(problems).isEmpty();
        for (Collected collected : Collected.values()) {
            assertThat(configuration.isEmpty(collected)).as(collected.name()).isTrue();
            assertThat(configuration.describe(collected)).isEqualTo("empty");
        }
    }

    @Test
    void testOnlyTheDataGivenIsCleared() throws Exception {
        collectSomething();

        configuration.apply(configuration.load(), Set.of(Collected.SFTP_SERVERS));

        assertThat(sftpServers.getServers()).isEmpty();
        assertThat(recentProjects.getProjects()).hasSize(1);
        assertThat(fritzBoxes.getConnections()).hasSize(1);
        assertThat(configuration.isEmpty(Collected.EPG_CACHE)).isFalse();
    }
}

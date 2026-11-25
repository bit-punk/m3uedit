package org.bitpunk.m3uedit.config;

import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.player.ExternalPlayer;
import org.bitpunk.m3uedit.player.ExternalPlayerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationTest {

    @TempDir
    Path tempDir;
    private ExternalPlayer player;
    private RecentProjects recentProjects;
    private FakeData servers;
    private FakeData cache;
    private Settings settings;
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        player = new ExternalPlayer(new YamlConfigManager<>(tempDir.resolve("external_player.yaml"),
                "player/external_player.yaml", ExternalPlayerConfig.class, ExternalPlayerConfig::new));
        recentProjects = new RecentProjects(new YamlConfigManager<>(tempDir.resolve("recent_projects.yaml"),
                RecentProjectsConfig.class, RecentProjectsConfig::new), Clock.systemUTC());
        servers = new FakeData("test.servers", "Servers");
        cache = new FakeData("test.cache", "Cache");
        settings = new Settings(tempDir.resolve("settings.yaml"));
        configuration = new Configuration(player, recentProjects, () -> List.of(servers, cache), settings);
    }

    /**
     * Data collected by a plugin.
     */
    private static class FakeData implements CollectedData {
        private final String id;
        private final String name;
        final List<String> entries = new ArrayList<>();
        boolean failing;

        FakeData(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String describe() {
            return entries.size() + " collected";
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        @Override
        public void clear() throws IOException {
            if (failing) {
                throw new IOException("in use");
            }
            entries.clear();
        }
    }

    private Path project(String name) throws Exception {
        return Files.writeString(tempDir.resolve(name + ".m3uproj"), "name: " + name);
    }

    private void collectSomething() throws Exception {
        recentProjects.add(project("tv"));
        servers.entries.add("nas");
        cache.entries.add("guide.xml");
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
    void testTheRecentlyUsedProjectsComeFirstThenTheDataOfThePlugins() throws Exception {
        collectSomething();

        assertThat(configuration.collectedData()).extracting(CollectedData::getId)
                .containsExactly(Configuration.RECENT_PROJECTS, "test.servers", "test.cache");
        assertThat(configuration.collectedData().getFirst().describe()).isEqualTo("1 entry");
        assertThat(configuration.collectedData().getFirst().getName()).isEqualTo("Recently used projects");
    }

    @Test
    void testTheCollectedDataGivenIsCleared() throws Exception {
        collectSomething();

        List<String> problems = configuration.apply(configuration.load(),
                Set.of(Configuration.RECENT_PROJECTS, "test.servers"));

        assertThat(problems).isEmpty();
        assertThat(recentProjects.getProjects()).isEmpty();
        assertThat(servers.isEmpty()).isTrue();
        assertThat(cache.isEmpty()).as("not given").isFalse();
    }

    @Test
    void testDataThatCannotBeClearedIsReported() throws Exception {
        collectSomething();
        cache.failing = true;

        List<String> problems = configuration.apply(configuration.load(), Set.of("test.servers", "test.cache"));

        assertThat(problems).singleElement().asString().contains("Cache", "in use");
        assertThat(servers.isEmpty()).as("the others are cleared anyway").isTrue();
    }

    @Test
    void testTheDataOfThePluginsIsCollectedOnce() {
        // the recently used SFTP servers are offered by the SFTP input and the SFTP output
        assertThat(Configuration.dataOfThePlugins()).extracting(CollectedData::getId).containsExactly(
                "org.bitpunk.m3uedit.fritzbox.recentConnections", "org.bitpunk.m3uedit.sftp.recentServers",
                "org.bitpunk.m3uedit.epg.cache");
    }
}

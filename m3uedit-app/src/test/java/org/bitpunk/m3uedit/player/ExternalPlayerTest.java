package org.bitpunk.m3uedit.player;

import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.api.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalPlayerTest {
    private static final String URI = "http://example.com/stream.m3u8";

    @Test
    void testBuildCommandWithDefaultConfig() {
        List<String> command = ExternalPlayer.buildCommand(new ExternalPlayerConfig(), entry(URI, null));
        assertThat(command).containsExactly("vlc", URI);
    }

    @Test
    void testBuildCommandWithNullConfigUsesDefaults() {
        List<String> command = ExternalPlayer.buildCommand(null, entry(URI, null));
        assertThat(command).containsExactly("vlc", URI);
    }

    @Test
    void testBuildCommandWithCustomCommandArgumentsAndVlcOptions() {
        ExternalPlayerConfig config = ExternalPlayerConfig.builder()
                .command("/usr/bin/cvlc")
                .arguments(List.of("--no-video-title-show"))
                .build();
        Map<String, String> vlcOpts = new LinkedHashMap<>();
        vlcOpts.put("http-user-agent", "Mozilla/5.0");
        vlcOpts.put("network-caching", "1000");

        List<String> command = ExternalPlayer.buildCommand(config, entry(URI, vlcOpts));

        assertThat(command).containsExactly("/usr/bin/cvlc", "--no-video-title-show", URI,
                ":http-user-agent=Mozilla/5.0", ":network-caching=1000");
    }

    @Test
    void testBuildCommandWithoutVlcOptions() {
        ExternalPlayerConfig config = ExternalPlayerConfig.builder().command("mpv").passVlcOptions(false).build();
        List<String> command = ExternalPlayer.buildCommand(config, entry(URI, Map.of("http-user-agent", "x")));
        assertThat(command).containsExactly("mpv", URI);
    }

    @Test
    void testBuildCommandBlankCommandFallsBackToVlc() {
        ExternalPlayerConfig config = ExternalPlayerConfig.builder().command(" ").build();
        assertThat(ExternalPlayer.buildCommand(config, entry(URI, null))).containsExactly("vlc", URI);
    }

    @Test
    void testBuildCommandWithoutUriThrows() {
        assertThatThrownBy(() -> ExternalPlayer.buildCommand(new ExternalPlayerConfig(), entry(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDefaultConfigIsLoadedFromResource(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("external_player.yaml");
        YamlConfigManager<ExternalPlayerConfig> configManager = new YamlConfigManager<>(configPath,
                "player/external_player.yaml", ExternalPlayerConfig.class, ExternalPlayerConfig::new);

        ExternalPlayerConfig config = configManager.loadConfig();

        assertThat(config.getCommand()).isEqualTo("vlc");
        assertThat(config.getArguments()).isEmpty();
        assertThat(config.isPassVlcOptions()).isTrue();
        assertThat(configPath).exists();
    }

    private static Entry entry(String uri, Map<String, String> vlcOpts) {
        Entry entry = new Entry();
        entry.setName("Test");
        entry.setChannelUri(uri);
        entry.setVlcOpts(vlcOpts);
        return entry;
    }
}

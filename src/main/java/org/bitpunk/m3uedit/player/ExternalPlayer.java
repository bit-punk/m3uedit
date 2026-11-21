package org.bitpunk.m3uedit.player;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.Entry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starts an external player (vlc by default) to view the stream of an {@link Entry}.
 * The player is configured in {@code ~/.m3uedit/external_player.yaml}.
 */
@Slf4j
public class ExternalPlayer {
    private static final String CONFIG_FILE_NAME = "external_player.yaml";
    private static final String CONFIG_RESOURCE = "player/external_player.yaml";

    private final YamlConfigManager<ExternalPlayerConfig> configManager;

    public ExternalPlayer() {
        this(new YamlConfigManager<>(getConfigPath(), CONFIG_RESOURCE,
                ExternalPlayerConfig.class, ExternalPlayerConfig::new));
    }

    public ExternalPlayer(YamlConfigManager<ExternalPlayerConfig> configManager) {
        this.configManager = configManager;
    }

    public static Path getConfigPath() {
        return AppConfigPaths.getAppConfigPath(CONFIG_FILE_NAME);
    }

    /**
     * @return the configuration as stored now
     */
    public ExternalPlayerConfig loadConfig() {
        return configManager.loadConfig();
    }

    /**
     * Stores the configuration; it is used by the next {@link #play(Entry)}.
     */
    public void saveConfig(ExternalPlayerConfig config) {
        configManager.saveConfig(config);
    }

    public void play(Entry entry) throws IOException {
        // reload on every call so changes to the config file take effect without restart
        List<String> command = buildCommand(configManager.loadConfig(), entry);
        log.info("starting external player: {}", command);
        new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    static List<String> buildCommand(ExternalPlayerConfig config, Entry entry) {
        if (entry == null || StringUtils.isBlank(entry.getChannelUri())) {
            throw new IllegalArgumentException("Entry has no channel uri");
        }
        ExternalPlayerConfig effectiveConfig = config != null ? config : new ExternalPlayerConfig();
        String executable = StringUtils.defaultIfBlank(effectiveConfig.getCommand(), ExternalPlayerConfig.DEFAULT_COMMAND);

        List<String> command = new ArrayList<>();
        command.add(executable);
        if (effectiveConfig.getArguments() != null) {
            command.addAll(effectiveConfig.getArguments());
        }
        command.add(entry.getChannelUri());
        // vlc applies ":option=value" arguments following an MRL to that item
        if (effectiveConfig.isPassVlcOptions() && entry.getVlcOpts() != null) {
            for (Map.Entry<String, String> vlcOpt : entry.getVlcOpts().entrySet()) {
                command.add(":" + vlcOpt.getKey() + "=" + vlcOpt.getValue());
            }
        }
        return command;
    }
}

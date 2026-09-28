package org.bitpunk.m3uedit.plugins.filter.channelname;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.channelname.model.ChannelNameFilterConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ChannelNameFilter extends AbstractPlugin implements Filter {
    public static final String RESOURCE_PATH = "plugin/filter/channelname/channel_name_filter.yaml";
    public static final String CONFIG_FILE_NAME = "channel_name_filter.yaml";

    @XStreamOmitField
    private final String name = "ChannelName Filter";
    @XStreamOmitField
    private final String description = "Filters entries based on the channelName using a List of regular expressions";

    @XStreamOmitField
    private transient YamlConfigManager<ChannelNameFilterConfig> configManager;

    @XStreamOmitField
    private transient ChannelNameFilterConfig config;

    public ChannelNameFilter() {
        this(createDefaultConfigManager(null));
    }

    public ChannelNameFilter(Path userConfigPath) {
        this(createDefaultConfigManager(userConfigPath));
    }

    public ChannelNameFilter(YamlConfigManager<ChannelNameFilterConfig> configManager) {
        this.configManager = configManager != null ? configManager : createDefaultConfigManager(null);
        this.config = this.configManager.loadConfig();
    }

    private static YamlConfigManager<ChannelNameFilterConfig> createDefaultConfigManager(Path userConfigPath) {
        Path path = userConfigPath != null ? userConfigPath : AppConfigPaths.getPluginConfigPath(CONFIG_FILE_NAME);
        return new YamlConfigManager<>(path, RESOURCE_PATH, ChannelNameFilterConfig.class, ChannelNameFilterConfig::new);
    }

    public YamlConfigManager<ChannelNameFilterConfig> getConfigManager() {
        if (configManager == null) {
            configManager = createDefaultConfigManager(null);
        }
        return configManager;
    }

    public ChannelNameFilterConfig getConfig() {
        if (config == null) {
            config = getConfigManager().loadConfig();
        }
        return config;
    }

    public void setConfig(ChannelNameFilterConfig config) {
        this.config = config;
    }

    public List<String> getExcludeRegexes() {
        ChannelNameFilterConfig currentConfig = getConfig();
        if (currentConfig != null && currentConfig.getExcludeRegexes() != null) {
            return currentConfig.getExcludeRegexes();
        }
        return List.of();
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        if (list == null) {
            return List.of();
        }
        List<String> excludeRegexes = getExcludeRegexes();
        return list.stream()
                .filter(entry -> excludeRegexes.stream().noneMatch(regex -> Pattern.matches(regex, entry.getName())))
                .toList();
    }

    @Override
    public void configure() {
        log.debug("Opening configuration dialog for ChannelNameFilter");
        ChannelNameFilterConfigDialogController dialog = new ChannelNameFilterConfigDialogController(this);
        dialog.showAndWait();
    }
}

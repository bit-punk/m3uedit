package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class PreferredChannelOrderSorter extends AbstractPlugin implements Sorter {
    public static final String RESOURCE_PATH = "plugin/sorter/preferredchannelorder/preferred_channel_order.yaml";
    public static final String CONFIG_FILE_NAME = "preferred_channel_order.yaml";

    @XStreamOmitField
    private final String name = "Preferred Channel Order Sorter";
    @XStreamOmitField
    private final String description = "Sorts list according to the preferred channel order";

    @XStreamOmitField
    private transient YamlConfigManager<PreferredChannelOrderConfig> configManager;

    @XStreamOmitField
    private transient PreferredChannelOrderConfig config;

    private String selectedProfileId = "deutschland_standard";

    public PreferredChannelOrderSorter() {
        this(createDefaultConfigManager(null));
    }

    public PreferredChannelOrderSorter(Path userConfigPath) {
        this(createDefaultConfigManager(userConfigPath));
    }

    public PreferredChannelOrderSorter(YamlConfigManager<PreferredChannelOrderConfig> configManager) {
        this.configManager = configManager != null ? configManager : createDefaultConfigManager(null);
        this.config = this.configManager.loadConfig();
        if (this.config != null && this.config.getDefaultProfile() != null) {
            this.selectedProfileId = this.config.getDefaultProfile();
        }
    }

    private static YamlConfigManager<PreferredChannelOrderConfig> createDefaultConfigManager(Path userConfigPath) {
        Path path = userConfigPath != null ? userConfigPath : AppConfigPaths.getPluginConfigPath(CONFIG_FILE_NAME);
        return new YamlConfigManager<>(path, RESOURCE_PATH, PreferredChannelOrderConfig.class, PreferredChannelOrderConfig::new);
    }

    private Object readResolve() {
        if (configManager == null) {
            configManager = createDefaultConfigManager(null);
        }
        if (config == null) {
            config = configManager.loadConfig();
        }
        return this;
    }

    public YamlConfigManager<PreferredChannelOrderConfig> getConfigManager() {
        if (configManager == null) {
            configManager = createDefaultConfigManager(null);
        }
        return configManager;
    }

    public PreferredChannelOrderConfig getConfig() {
        if (config == null) {
            if (configManager == null) {
                configManager = createDefaultConfigManager(null);
            }
            config = configManager.loadConfig();
        }
        return config;
    }

    public Optional<ChannelProfile> getSelectedProfile() {
        PreferredChannelOrderConfig currentConfig = getConfig();
        if (currentConfig == null || currentConfig.getProfiles() == null) {
            return Optional.empty();
        }
        return currentConfig.getProfiles().stream()
                .filter(p -> p.getId() != null && p.getId().equals(selectedProfileId))
                .findFirst();
    }

    @Override
    public List<Entry> sort(List<Entry> entries) {
        if (entries == null) {
            return List.of();
        }
        List<String> preferredOrder = getSelectedProfile()
                .map(ChannelProfile::getChannels)
                .orElse(List.of());

        if (preferredOrder.isEmpty()) {
            return entries;
        }

        return entries.stream()
                .sorted((e1, e2) -> {
                    int index1 = preferredOrder.indexOf(e1.getName());
                    int index2 = preferredOrder.indexOf(e2.getName());
                    if (index1 == -1 && index2 == -1) {
                        return e1.getName().compareTo(e2.getName());
                    }
                    if (index1 == -1) {
                        return 1;
                    }
                    if (index2 == -1) {
                        return -1;
                    }
                    return Integer.compare(index1, index2);
                })
                .toList();
    }

    @Override
    public void configure() {
        log.debug("Opening configuration dialog for PreferredChannelOrderSorter");
        PreferredChannelOrderConfigDialogController dialog = new PreferredChannelOrderConfigDialogController(this);
        dialog.showAndWait();
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }
}

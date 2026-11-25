package org.bitpunk.m3uedit.plugins.filter.channelname;

import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.filter.channelname.model.ChannelNameFilterConfig;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes channels whose name matches one of the regular expressions of its configuration-file
 * ({@value #CONFIG_FILE_NAME}); nothing of it is stored in the project-file.
 */
@Slf4j
@PluginType(id = "ChannelNameFilter", nameKey = "filter.channelName.name", texts = "i18n.messages")
public class ChannelNameFilter implements Filter {
    public static final String RESOURCE_PATH = "plugin/filter/channelname/channel_name_filter.yaml";
    public static final String CONFIG_FILE_NAME = "channel_name_filter.yaml";

    /**
     * -- GETTER --
     *
     * @return the context passed to {@link #init(PluginContext)}
     */
    @Getter
    private PluginContext context;

    /**
     * -- SETTER --
     *  Sets the configuration used by this filter, without writing it to the configuration-file.
     */
    @Setter
    private ChannelNameFilterConfig config;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("filter.channelName.name");
    }

    @Override
    public String getDescription() {
        return context.text("filter.channelName.description");
    }

    /**
     * @return the configuration, read from the configuration-file on first use
     */
    public ChannelNameFilterConfig getConfig() {
        if (config == null) {
            config = context.loadConfig(CONFIG_FILE_NAME, ChannelNameFilterConfig.class, RESOURCE_PATH,
                    ChannelNameFilterConfig::new);
        }
        return config;
    }

    /**
     * Sets the configuration used by this filter and writes it to the configuration-file.
     */
    public void saveConfig(ChannelNameFilterConfig config) {
        setConfig(config);
        context.saveConfig(CONFIG_FILE_NAME, config);
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
    public String describeRemoval(Entry removed, List<Entry> input) {
        return getExcludeRegexes().stream()
                .filter(regex -> Pattern.matches(regex, removed.getName()))
                .findFirst()
                .map(regex -> context.text("filter.channelName.reason", regex))
                .orElse(null);
    }

    @Override
    public boolean configure(Window owner) {
        log.debug("Opening configuration dialog for ChannelNameFilter");
        ChannelNameFilterConfigDialogController dialog = new ChannelNameFilterConfigDialogController(this);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait().isPresent();
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    /**
     * Plugins are compared by identity, only a short description is returned.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}

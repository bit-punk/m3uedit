package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

import javafx.stage.Window;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.api.Sorter;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.PreferredChannelOrderConfig;

import java.util.List;
import java.util.Optional;

/**
 * Sorts the channels in the order of a profile of its configuration-file ({@value #CONFIG_FILE_NAME}); channels
 * not in the profile follow in alphabetical order. Only the selected profile is stored in the project-file.
 */
@Slf4j
@PluginType(id = "PreferredChannelOrderSorter", nameKey = "sorter.preferredChannelOrder.name",
        texts = "i18n.messages")
public class PreferredChannelOrderSorter implements Sorter {
    public static final String RESOURCE_PATH = "plugin/sorter/preferredchannelorder/preferred_channel_order.yaml";
    public static final String CONFIG_FILE_NAME = "preferred_channel_order.yaml";
    /**
     * The profile used if neither the project nor the configuration selects one.
     */
    static final String DEFAULT_PROFILE_ID = "deutschland_standard";

    private PluginContext context;

    /**
     * -- SETTER --
     *  Sets the configuration used by this sorter, without writing it to the configuration-file.
     */
    @Setter
    private PreferredChannelOrderConfig config;

    /**
     * The id of the selected profile, null for the default profile of the configuration.
     * -- SETTER --
     *
     * @param selectedProfileId the id of the profile to sort by, null for the default profile of the configuration

     */
    @Setter
    private String selectedProfileId;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the context passed to {@link #init(PluginContext)}
     */
    public PluginContext getContext() {
        return context;
    }

    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("sorter.preferredChannelOrder.name");
    }

    @Override
    public String getDescription() {
        return context.text("sorter.preferredChannelOrder.description");
    }

    @Override
    public Class<PreferredChannelOrderSorterSettings> getSettingsType() {
        return PreferredChannelOrderSorterSettings.class;
    }

    @Override
    public PreferredChannelOrderSorterSettings getSettings() {
        return new PreferredChannelOrderSorterSettings(getSelectedProfileId());
    }

    @Override
    public void setSettings(Record settings) {
        // a missing profile is replaced by the default profile, see getSelectedProfileId()
        setSelectedProfileId(((PreferredChannelOrderSorterSettings) settings).selectedProfileId());
    }

    /**
     * @return the id of the selected profile; the default profile of the configuration if none has been selected
     */
    public String getSelectedProfileId() {
        if (selectedProfileId != null) {
            return selectedProfileId;
        }
        PreferredChannelOrderConfig currentConfig = getConfig();
        return currentConfig != null && currentConfig.getDefaultProfile() != null
                ? currentConfig.getDefaultProfile() : DEFAULT_PROFILE_ID;
    }

    /**
     * @return the configuration, read from the configuration-file on first use
     */
    public PreferredChannelOrderConfig getConfig() {
        if (config == null) {
            config = context.loadConfig(CONFIG_FILE_NAME, PreferredChannelOrderConfig.class, RESOURCE_PATH,
                    PreferredChannelOrderConfig::new);
        }
        return config;
    }

    /**
     * Sets the configuration used by this sorter and writes it to the configuration-file.
     */
    public void saveConfig(PreferredChannelOrderConfig config) {
        setConfig(config);
        context.saveConfig(CONFIG_FILE_NAME, config);
    }

    public Optional<ChannelProfile> getSelectedProfile() {
        PreferredChannelOrderConfig currentConfig = getConfig();
        if (currentConfig == null || currentConfig.getProfiles() == null) {
            return Optional.empty();
        }
        String profileId = getSelectedProfileId();
        return currentConfig.getProfiles().stream()
                .filter(p -> p.getId() != null && p.getId().equals(profileId))
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
    public boolean configure(Window owner) {
        log.debug("Opening configuration dialog for PreferredChannelOrderSorter");
        PreferredChannelOrderConfigDialogController dialog = new PreferredChannelOrderConfigDialogController(this);
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

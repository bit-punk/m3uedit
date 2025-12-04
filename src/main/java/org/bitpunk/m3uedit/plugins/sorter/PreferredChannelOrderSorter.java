package org.bitpunk.m3uedit.plugins.sorter;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Data
@EqualsAndHashCode(callSuper = true)
public class PreferredChannelOrderSorter extends AbstractPlugin implements Sorter {
    private static final String FILTER_RESOURCE_NAME = "plugin/sorter/preferred_channel_order.properties";
    private final List<List<String>> profiles;
    private String selectedProfile = "profile1";
    @XStreamOmitField
    private final String name = "Preferred Channel Order Sorter";
    @XStreamOmitField
    private final String description = "sorts list according to the preferred channel order";


    public PreferredChannelOrderSorter() {
        this.profiles = readProfilesFromProperties();
    }

    @Override
    public List<Entry> sort(List<Entry> entries) {
        List<String> preferredOrder = profiles.get(Integer.parseInt(selectedProfile.substring(7)) - 1);
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

    private List<List<String>> readProfilesFromProperties() {
        Properties properties = new Properties();
        try {
            properties.load(getClass().getClassLoader().getResourceAsStream(FILTER_RESOURCE_NAME));
            return properties.values().stream()
                    .map(value -> parseChannelNames((String) value))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load properties file " + FILTER_RESOURCE_NAME, e);
        }
    }

    private List<String> parseChannelNames(String channelNames) {
        return Arrays.stream(channelNames.split(","))
                .map(String::trim)
                .toList();
    }
}

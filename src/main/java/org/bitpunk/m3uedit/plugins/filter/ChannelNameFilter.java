package org.bitpunk.m3uedit.plugins.filter;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelNameFilter extends AbstractPlugin implements Filter {
    private static final String FILTER_RESOURCE_NAME = "plugin/filter/ChannelNameFilter.properties";

    @XStreamOmitField
    private final List<String> excludeRegexes = getFilterExpressionsFromResourceFile();
    @XStreamOmitField
    private final String name = "ChannelName Filter";
    @XStreamOmitField
    private final String description = "Filters entries based on the channelName using a List of regular expressions";

    private static List<String> getFilterExpressionsFromResourceFile() {
        Properties prop = new Properties();
        try {
            prop.load(ChannelNameFilter.class.getClassLoader().getResourceAsStream(FILTER_RESOURCE_NAME));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load properties file " + FILTER_RESOURCE_NAME, e);
        }
        return prop.values().stream().map(o -> (String) o).toList();
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream().filter(entry -> excludeRegexes.stream().noneMatch(regex -> Pattern.matches(regex, entry.getName()))).toList();
    }
}
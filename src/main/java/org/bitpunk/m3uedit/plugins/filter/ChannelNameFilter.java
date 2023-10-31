package org.bitpunk.m3uedit.plugins.filter;

import lombok.Data;
import org.bitpunk.m3uedit.model.Entry;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
public class ChannelNameFilter implements Filter {
    private static final String filterResourceName = "filter/name_filter.properties";

    private final List<String> excludeRegexes = getFilterExpressionsFromResourcefile();
    private final String name = "Name Filter";
    private final String description = "Filters entries based on the channelName using a List of regular expressions";

    private static List<String> getFilterExpressionsFromResourcefile() {
        Properties prop = new Properties();
        try {
            prop.load(ChannelNameFilter.class.getClassLoader().getResourceAsStream(filterResourceName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return prop.values().stream().map(o -> (String) o).collect(Collectors.toList());
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream().filter(entry -> excludeRegexes.stream().noneMatch(regex -> Pattern.matches(regex, entry.getName()))).collect(Collectors.toList());
    }
}
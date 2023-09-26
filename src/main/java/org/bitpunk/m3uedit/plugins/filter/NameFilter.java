package org.bitpunk.m3uedit.plugins.filter;

import lombok.Data;
import org.bitpunk.m3uedit.model.Entry;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
public class NameFilter implements Filter {

    private final List<String> excludeRegexes = Arrays.asList("(?i).*QVC.*", ".*HSE.*", ".*1-2-3.*");
    private final String name = "Name Filter";
    private final String description = "Filters entries based on the channelName using a List of regular expressions";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(entry -> excludeRegexes.stream().noneMatch(regex -> Pattern.matches(regex, entry.getChannelName())))
                .collect(Collectors.toList());
    }
}

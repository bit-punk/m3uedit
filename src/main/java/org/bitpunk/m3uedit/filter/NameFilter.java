package org.bitpunk.m3uedit.filter;

import org.bitpunk.m3uedit.model.Entry;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NameFilter implements Filter{

    private final List<String> excludeRegexes = Arrays.asList(".*QVC.*", ".*HSE.*");
    private final String description = "Filters entries based on the channelName using a List of regular expressions";
    @Override
    public List<Entry> filter(List<Entry> list){
        return list.stream()
                .filter(entry -> !excludeRegexes.stream().anyMatch(regex -> Pattern.matches(regex, entry.getChannelName())))
                .collect(Collectors.toList());
    }
}

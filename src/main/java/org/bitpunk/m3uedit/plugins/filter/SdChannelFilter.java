package org.bitpunk.m3uedit.plugins.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = false)
public class SdChannelFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(SdChannelFilter.class);
    private final String name = "SD Channel Filter";
    private final String description = "Filters for SD channels (criteria: the same channelName exists with a 'HD' suffix)";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(entry -> list.stream().noneMatch(e -> {
                    String entryName = entry.getName();
                    String currentEntryName = e.getName();
                    return !entryName.equals(currentEntryName) && currentEntryName.startsWith(entryName);
                }))
                .collect(Collectors.toList());
    }

}

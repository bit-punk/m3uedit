package org.bitpunk.m3uedit.plugins.filter.sdchannel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.List;

@Slf4j
@Getter
@Setter
public class SdChannelFilter extends AbstractPlugin implements Filter {
    @JsonIgnore
    private final String name = "SD Channel Filter";
    @JsonIgnore
    private final String description = "Filters for SD channels (criteria: the same channelName exists with a 'HD' suffix)";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(entry -> list.stream().noneMatch(e -> {
                    String entryName = entry.getName();
                    String currentEntryName = e.getName();
                    return !entryName.equals(currentEntryName) && currentEntryName.startsWith(entryName);
                }))
                .toList();
    }

}

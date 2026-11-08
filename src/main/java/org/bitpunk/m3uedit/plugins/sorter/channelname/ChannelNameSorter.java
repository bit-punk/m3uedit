package org.bitpunk.m3uedit.plugins.sorter.channelname;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;

import java.util.Comparator;
import java.util.List;

@Getter
@Setter
public class ChannelNameSorter extends AbstractPlugin implements Sorter {
    @JsonIgnore
    private final String name = "ChannelName Sorter";
    @JsonIgnore
    private final String description = "sorts list alphabetically";

    @Override
    public List<Entry> sort(List<Entry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(Entry::getName))
                .toList();
    }

}
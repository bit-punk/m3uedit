package org.bitpunk.m3uedit.plugins.sorter;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;

import java.util.Comparator;
import java.util.List;

@Data
public class ChannelNameSorter extends AbstractPlugin implements Sorter {
    @XStreamOmitField
    private final String name = "ChannelName Sorter";
    @XStreamOmitField
    private final String description = "sorts list alphabetically";

    @Override
    public List<Entry> sort(List<Entry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(Entry::getName))
                .toList();
    }

}
package org.bitpunk.m3uedit.plugins.sorter.channelname;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;

import java.util.Comparator;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
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
package org.bitpunk.m3uedit.plugins.sorter.channelname;

import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.PluginType;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;

import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@PluginType(id = "ChannelNameSorter", nameKey = "sorter.channelName.name")
public class ChannelNameSorter extends AbstractPlugin implements Sorter {
    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("sorter.channelName.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("sorter.channelName.description");
    }

    @Override
    public List<Entry> sort(List<Entry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(Entry::getName))
                .toList();
    }

}
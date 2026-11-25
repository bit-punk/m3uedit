package org.bitpunk.m3uedit.plugins.sorter.channelname;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.api.Sorter;

import java.util.Comparator;
import java.util.List;

@PluginType(id = "ChannelNameSorter", nameKey = "sorter.channelName.name", texts = "org.bitpunk.m3uedit.plugins.messages")
public class ChannelNameSorter implements Sorter {
    private PluginContext context;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("sorter.channelName.name");
    }

    @Override
    public String getDescription() {
        return context.text("sorter.channelName.description");
    }

    @Override
    public List<Entry> sort(List<Entry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(Entry::getName))
                .toList();
    }

    /**
     * Plugins are compared by identity, only a short description is returned.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}

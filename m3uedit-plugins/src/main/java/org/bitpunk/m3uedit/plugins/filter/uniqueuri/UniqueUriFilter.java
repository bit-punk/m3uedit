package org.bitpunk.m3uedit.plugins.filter.uniqueuri;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;

import java.util.List;

@PluginType(id = "UniqueUriFilter", nameKey = "filter.uniqueUri.name", texts = "org.bitpunk.m3uedit.plugins.messages")
public class UniqueUriFilter extends AbstractUniqueFilter {
    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return getContext().text("filter.uniqueUri.name");
    }

    @Override
    public String getDescription() {
        return getContext().text("filter.uniqueUri.description");
    }

    @Override
    public int order() {
        return ORDER_DEDUPLICATION;
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(distinctByKey(Entry::getChannelUri))
                .toList();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        return describeDuplicate(removed, input, Entry::getChannelUri, "filter.uniqueUri.reason");
    }

}

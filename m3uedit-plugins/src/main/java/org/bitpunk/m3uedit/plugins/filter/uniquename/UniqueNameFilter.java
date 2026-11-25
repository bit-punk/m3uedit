package org.bitpunk.m3uedit.plugins.filter.uniquename;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;

import java.util.List;

@PluginType(id = "UniqueNameFilter", nameKey = "filter.uniqueName.name", texts = "org.bitpunk.m3uedit.plugins.messages")
public class UniqueNameFilter extends AbstractUniqueFilter {
    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return getContext().text("filter.uniqueName.name");
    }

    @Override
    public String getDescription() {
        return getContext().text("filter.uniqueName.description");
    }

    @Override
    public int order() {
        return ORDER_DEDUPLICATION + 10;
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(distinctByKey(Entry::getName))
                .toList();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        return describeDuplicate(removed, input, Entry::getName, "filter.uniqueName.reason");
    }

}

package org.bitpunk.m3uedit.plugins.filter.uniqueuri;

import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.PluginType;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;
import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.List;

@Getter
@Setter
@PluginType(id = "UniqueUriFilter", nameKey = "filter.uniqueUri.name")
public class UniqueUriFilter extends AbstractUniqueFilter implements Filter {
    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("filter.uniqueUri.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("filter.uniqueUri.description");
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

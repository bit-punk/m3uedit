package org.bitpunk.m3uedit.plugins.filter.uniquename;

import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;
import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.List;

@Getter
@Setter
public class UniqueNameFilter extends AbstractUniqueFilter implements Filter {
    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("filter.uniqueName.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("filter.uniqueName.description");
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

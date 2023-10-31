package org.bitpunk.m3uedit.plugins.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;

import java.util.List;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = false)
public class UniqueNameFilter extends AbstractUniqueFilter implements Filter {
    private final String name = "Unique Name Filter";
    private final String description = "Filters for unique entries (criteria: channelName)";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(distinctByKey(Entry::getName))
                .collect(Collectors.toList());
    }

}

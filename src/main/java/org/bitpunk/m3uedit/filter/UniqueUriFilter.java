package org.bitpunk.m3uedit.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;

import java.util.List;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = false)
public class UniqueUriFilter extends AbstractUniqueFilter implements Filter {
    private final String description = "Filters for unique entries (criteria: channelUri)";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(distinctByKey(Entry::getChannelUri))
                .collect(Collectors.toList());
    }

}

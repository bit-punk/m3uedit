package org.bitpunk.m3uedit.plugins.filter.uniqueuri;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;
import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.List;

@Getter
@Setter
public class UniqueUriFilter extends AbstractUniqueFilter implements Filter {
    @JsonIgnore
    private final String name = "Unique URI Filter";
    @JsonIgnore
    private final String description = "Filters for unique entries (criteria: channelUri)";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(distinctByKey(Entry::getChannelUri))
                .toList();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        return describeDuplicate(removed, input, Entry::getChannelUri, "URL");
    }

}

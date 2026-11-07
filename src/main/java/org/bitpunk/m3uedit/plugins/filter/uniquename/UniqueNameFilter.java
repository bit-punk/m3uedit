package org.bitpunk.m3uedit.plugins.filter.uniquename;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;
import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.List;

@Getter
@Setter
public class UniqueNameFilter extends AbstractUniqueFilter implements Filter {
    @XStreamOmitField
    private final String name = "Unique Name Filter";
    @XStreamOmitField
    private final String description = "Filters for unique entries (criteria: channelName)";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(distinctByKey(Entry::getName))
                .toList();
    }

}

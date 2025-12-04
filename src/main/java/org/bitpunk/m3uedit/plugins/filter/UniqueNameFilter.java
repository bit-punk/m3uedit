package org.bitpunk.m3uedit.plugins.filter;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
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

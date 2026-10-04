package org.bitpunk.m3uedit.plugins.filter.sdchannel;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class SdChannelFilter extends AbstractPlugin implements Filter {
    private static final Logger log = LoggerFactory.getLogger(SdChannelFilter.class);
    @XStreamOmitField
    private final String name = "SD Channel Filter";
    @XStreamOmitField
    private final String description = "Filters for SD channels (criteria: the same channelName exists with a 'HD' suffix)";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream()
                .filter(entry -> list.stream().noneMatch(e -> {
                    String entryName = entry.getName();
                    String currentEntryName = e.getName();
                    return !entryName.equals(currentEntryName) && currentEntryName.startsWith(entryName);
                }))
                .toList();
    }

}

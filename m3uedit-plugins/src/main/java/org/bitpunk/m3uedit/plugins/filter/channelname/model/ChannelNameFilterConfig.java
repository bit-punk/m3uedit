package org.bitpunk.m3uedit.plugins.filter.channelname.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelNameFilterConfig {
    @Builder.Default
    private List<String> excludeRegexes = new ArrayList<>();
}

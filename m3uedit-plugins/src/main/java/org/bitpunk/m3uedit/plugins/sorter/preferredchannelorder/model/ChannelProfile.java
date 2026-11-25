package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model;

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
public class ChannelProfile {
    private String id;
    private String name;
    @Builder.Default
    private List<String> channels = new ArrayList<>();
}

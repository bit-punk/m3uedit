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
public class PreferredChannelOrderConfig {
    @Builder.Default
    private String defaultProfile = "deutschland_standard";
    @Builder.Default
    private List<org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.model.ChannelProfile> profiles = new ArrayList<>();
}

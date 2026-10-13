package org.bitpunk.m3uedit.player;

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
public class ExternalPlayerConfig {
    public static final String DEFAULT_COMMAND = "vlc";

    /** executable name (resolved via PATH) or absolute path of the external player */
    @Builder.Default
    private String command = DEFAULT_COMMAND;
    /** additional arguments passed to the player before the stream uri */
    @Builder.Default
    private List<String> arguments = new ArrayList<>();
    /** pass the entry's #EXTVLCOPT options as ":key=value" arguments (only understood by vlc) */
    @Builder.Default
    private boolean passVlcOptions = true;
}

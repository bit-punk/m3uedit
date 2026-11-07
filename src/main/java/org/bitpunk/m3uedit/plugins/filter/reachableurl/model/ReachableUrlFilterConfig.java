package org.bitpunk.m3uedit.plugins.filter.reachableurl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReachableUrlFilterConfig {
    public static final int DEFAULT_TIMEOUT_MILLIS = 2000;

    /** connect-timeout in milliseconds used to check whether the host of an entry is reachable */
    @Builder.Default
    private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
}

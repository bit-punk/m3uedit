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
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    /** timeout in milliseconds for connecting and for waiting on data of every request used to check a stream */
    @Builder.Default
    private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
}

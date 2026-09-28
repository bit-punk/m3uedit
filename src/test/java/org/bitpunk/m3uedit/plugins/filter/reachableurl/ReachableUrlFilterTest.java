package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReachableUrlFilterTest {

    @ParameterizedTest
    @CsvSource(value = {
            "http://www.google.de, 1000, true",
            "http://192.168.1.1:8080, 100, false"
    })
    @Disabled("test will not run properly on github")
    void testURIs(String url, int timeout, boolean expectedReachability) {
        ReachableUrlFilter reachableUrlFilter = new ReachableUrlFilter();
        assertThat(reachableUrlFilter.isUrlReachable(url, timeout)).isEqualTo(expectedReachability);
    }
}

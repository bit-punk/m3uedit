package org.bitpunk.m3uedit.plugins.filter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ReachableUrlFilterTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value =
            {
            "http://heise.de; 10000; true",
            "http://bdhjga.gz; 10000; false",
            "http://samuraitv.online:8000/Aniveltv19022024q/76juyu457hur7mngdar/153955; 10000; true",
            "rtsp://192.168.178.1:554/?avm=1&freq=250&bw=8&msys=dvbc&mtype=256qam&sr=6900&specinv=0&pids=0,16,17,18,20,105,6661,6660,6662,6663,6664,6665,6666,6667,6668,6669; 10000; true",
    })

    void testURIs(String url, int timeout, boolean expectedReachability) {
        ReachableUrlFilter reachableUrlFilter = new ReachableUrlFilter();
        assertThat(reachableUrlFilter.isUrlReachable(url, timeout), is(expectedReachability));
    }
}
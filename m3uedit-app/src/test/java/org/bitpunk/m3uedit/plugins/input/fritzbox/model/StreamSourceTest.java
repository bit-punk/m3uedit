package org.bitpunk.m3uedit.plugins.input.fritzbox.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamSourceTest {

    @Test
    void testConstructorAndGetters() {
        StreamSource streamSource = new StreamSource("tvsd.m3u", "SD streams");

        assertThat(streamSource.getName()).isEqualTo("tvsd.m3u");
        assertThat(streamSource.getDescription()).isEqualTo("SD streams");
    }

    @Test
    void testEqualsAndHashCode() {
        StreamSource source1 = new StreamSource("tvsd.m3u", "SD streams");
        StreamSource source2 = new StreamSource("tvsd.m3u", "SD streams");
        StreamSource source3 = new StreamSource("tvhd.m3u", "HD streams");

        assertThat(source1)
                .isEqualTo(source2)
                .hasSameHashCodeAs(source2)
                .isNotEqualTo(source3)
                .isNotEqualTo(null)
                .isNotEqualTo("otherType");
    }

    @Test
    void testToString() {
        StreamSource source = new StreamSource("tvsd.m3u", "SD streams");
        assertThat(source.toString()).contains("tvsd.m3u", "SD streams");
    }
}

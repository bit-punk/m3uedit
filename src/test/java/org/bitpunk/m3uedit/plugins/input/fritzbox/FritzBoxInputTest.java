package org.bitpunk.m3uedit.plugins.input.fritzbox;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FritzBoxInputTest {

    @Test
    void testBasicProperties() {
        FritzBoxInput input = new FritzBoxInput();
        assertThat(input.getName()).isEqualTo("FritzBox Input");
        assertThat(input.getDescription()).isEqualTo("Downloads a playlist from a FritzBox.");
        assertThat(input.getSource()).isNull();
        assertThat(input.getEnabled().get()).isFalse();

        input.getEnabled().set(true);
        assertThat(input.getEnabled().get()).isTrue();
    }

    @Test
    void testGetSourceWhenConfigured() {
        FritzBoxInput input = new FritzBoxInput();
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl("http://fritz.box/dvb/m3u/");
        connection.setSelectedStreamSources(List.of(new StreamSource("tvsd.m3u", "SD streams")));

        input.setFritzBoxConnection(connection);
        assertThat(input.getSource()).isEqualTo("base-url: http://fritz.box/dvb/m3u/, streams: tvsd.m3u");
    }

    @Test
    void testReadNullOrEmptyStreamSourcesReturnsEmptyList() {
        FritzBoxInput input = new FritzBoxInput();
        input.setSelectedStreamSources(null);
        List<Entry> entriesNull = input.read();
        assertThat(entriesNull).isNotNull().isEmpty();

        input.setSelectedStreamSources(Collections.emptyList());
        List<Entry> entriesEmpty = input.read();
        assertThat(entriesEmpty).isNotNull().isEmpty();
    }

    @Test
    void testEqualsAndHashCode() {
        FritzBoxInput input1 = new FritzBoxInput();
        FritzBoxConnection conn1 = new FritzBoxConnection();
        conn1.setBaseUrl("http://fritz.box/dvb/m3u/");
        input1.setFritzBoxConnection(conn1);

        FritzBoxInput input2 = new FritzBoxInput();
        input2.setEnabled(input1.getEnabled());
        FritzBoxConnection conn2 = new FritzBoxConnection();
        conn2.setBaseUrl("http://fritz.box/dvb/m3u/");
        input2.setFritzBoxConnection(conn2);

        FritzBoxInput input3 = new FritzBoxInput();
        input3.setEnabled(input1.getEnabled());
        FritzBoxConnection conn3 = new FritzBoxConnection();
        conn3.setBaseUrl("http://other.box/dvb/m3u/");
        input3.setFritzBoxConnection(conn3);

        assertThat(input1)
                .isEqualTo(input2)
                .hasSameHashCodeAs(input2)
                .isNotEqualTo(input3)
                .isNotEqualTo(null)
                .isNotEqualTo("otherString");
    }
}

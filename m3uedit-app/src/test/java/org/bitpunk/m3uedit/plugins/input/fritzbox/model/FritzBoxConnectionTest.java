package org.bitpunk.m3uedit.plugins.input.fritzbox.model;

import org.bitpunk.m3uedit.plugins.support.BeanValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FritzBoxConnectionTest {

    @Test
    void testValidConnection() {
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl("http://fritz.box/dvb/m3u/");
        connection.setSelectedStreamSources(List.of(
                new StreamSource("tvsd.m3u", "SD streams"),
                new StreamSource("tvhd.m3u", "HD streams")
        ));

        assertThat(BeanValidator.validate(connection)).isEmpty();

        assertThat(connection.getBaseUrl()).isEqualTo("http://fritz.box/dvb/m3u/");
        assertThat(connection.getSelectedStreamSources()).hasSize(2);
        assertThat(connection).hasToString("base-url: http://fritz.box/dvb/m3u/, streams: tvsd.m3u, tvhd.m3u");
    }

    @Test
    void testInvalidConnectionEmptyOrNonUrl() {
        FritzBoxConnection emptyConnection = new FritzBoxConnection();
        emptyConnection.setBaseUrl("");

        assertThat(BeanValidator.validate(emptyConnection)).isNotEmpty();

        FritzBoxConnection invalidUrlConnection = new FritzBoxConnection();
        invalidUrlConnection.setBaseUrl("not-a-valid-url");
        assertThat(BeanValidator.validate(invalidUrlConnection)).isNotEmpty();
    }

    @Test
    void testToStringWithoutStreams() {
        FritzBoxConnection connection = new FritzBoxConnection();
        connection.setBaseUrl("http://192.168.178.1/dvb/m3u/");

        assertThat(connection).hasToString("base-url: http://192.168.178.1/dvb/m3u/, streams: ");
    }

    @Test
    void testEqualsAndHashCode() {
        FritzBoxConnection connection1 = new FritzBoxConnection();
        connection1.setBaseUrl("http://fritz.box/dvb/m3u/");
        connection1.setSelectedStreamSources(List.of(new StreamSource("tvsd.m3u", "SD streams")));

        FritzBoxConnection connection2 = new FritzBoxConnection();
        connection2.setBaseUrl("http://fritz.box/dvb/m3u/");
        connection2.setSelectedStreamSources(List.of(new StreamSource("tvsd.m3u", "SD streams")));

        FritzBoxConnection connection3 = new FritzBoxConnection();
        connection3.setBaseUrl("http://other.box/dvb/m3u/");

        assertThat(connection1)
                .isEqualTo(connection2)
                .hasSameHashCodeAs(connection2)
                .isNotEqualTo(connection3)
                .isNotEqualTo(null)
                .isNotEqualTo("otherString");
    }
}

package org.bitpunk.m3uedit.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The settings records of plugins are stored by their components, flat next to the type of the plugin.
 */
class SettingsRecordMappingTest {

    enum Mode { FAST, CAREFUL }

    record Server(String host, int port) {
    }

    record Settings(String source, int timeout, boolean compressed, Mode mode, List<String> urls,
                    Map<String, String> options, Server server, List<Server> servers) {
    }

    @Test
    void testARecordIsWrittenByItsComponentsAndReadAgain() throws Exception {
        Settings settings = new Settings("/tmp/a.m3u", 5, true, Mode.CAREFUL, List.of("http://a", "http://b"),
                Map.of("k", "v"), new Server("nas", 22), List.of(new Server("x", 1)));

        String yaml = ProjectPersisterUtil.getSettingsMapper().writeValueAsString(settings);
        Settings read = ProjectPersisterUtil.getSettingsMapper().readValue(yaml, Settings.class);

        assertThat(yaml).contains("source: /tmp/a.m3u", "timeout: 5", "mode: CAREFUL", "host: nas");
        assertThat(read).isEqualTo(settings);
    }

    @Test
    void testMissingComponentsAreNullOrZero() throws Exception {
        Settings read = ProjectPersisterUtil.getSettingsMapper().readValue("source: /tmp/a.m3u\nunknown: 1\n",
                Settings.class);

        assertThat(read).isEqualTo(new Settings("/tmp/a.m3u", 0, false, null, null, null, null, null));
    }
}

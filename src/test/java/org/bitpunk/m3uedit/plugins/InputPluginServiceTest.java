package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.file.FileInput;
import org.bitpunk.m3uedit.plugins.input.fritzbox.FritzBoxInput;
import org.bitpunk.m3uedit.plugins.input.iptvorg.IptvOrgInput;
import org.bitpunk.m3uedit.plugins.input.sftp.SftpInput;
import org.bitpunk.m3uedit.plugins.input.url.UrlInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputPluginServiceTest {

    public static final String[] INPUT_NAMES = {"File Input", "FritzBox Input", "Url Input", "IPTV-org Input", "SFTP Input"};

    @Test
    void getAllPlugins() {
        List<? extends Plugin> allPlugins = InputPluginService.getInstance().getAllPlugins();
        assertThat(allPlugins).hasSize(5);
        assertThat(allPlugins)
                .extracting(Plugin::getName)
                .contains(INPUT_NAMES);

    }

    @Test
    void getAllPluginNames() {
        List<String> allPluginNames = InputPluginService.getInstance().getAllPluginNames();
        assertThat(allPluginNames)
                .hasSize(5)
                .contains(INPUT_NAMES);
    }

    @Test
    void getAllPluginTypes() {
        List<Class<? extends Input>> allPluginTypes = InputPluginService.getInstance().getAllPluginTypes();
        assertThat(allPluginTypes).containsExactlyInAnyOrder(
                FileInput.class, FritzBoxInput.class, UrlInput.class, IptvOrgInput.class, SftpInput.class);
    }
}

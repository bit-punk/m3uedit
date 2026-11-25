package org.bitpunk.m3uedit.plugins;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The built-in plugins are written against the plugin API ({@code org.bitpunk.m3uedit.api}). The internals of m3uedit
 * they still use are listed here; splitting the build (phase 3 of #46) has to empty this list. A new use of an internal
 * makes this test fail, as does a use removed without updating the list.
 */
class PluginInternalsTest {
    private static final Path PLUGINS = Path.of("src/main/java/org/bitpunk/m3uedit/plugins");
    private static final Pattern INTERNAL = Pattern.compile("org\\.bitpunk\\.m3uedit\\.(?!api\\.)([\\w.]*[A-Z]\\w*)");

    /**
     * Class of a plugin (relative to the plugins package) → internal class of m3uedit it uses.
     */
    private static final Set<String> ALLOWED = Set.of(
            // the dialog infrastructure: loading FXML, marking invalid fields, file choosers
            "filter/channelname/ChannelNameFilterConfigDialogController -> controller.AbstractDialogController",
            "filter/reachableurl/ReachableUrlFilterConfigDialogController -> controller.AbstractDialogController",
            "input/fritzbox/FritzboxInputDialogController -> controller.AbstractDialogController",
            "input/iptvorg/IptvOrgInputDialogController -> controller.AbstractDialogController",
            "input/url/UrlInputDialogController -> controller.AbstractDialogController",
            "sftp/SftpConnectionDialogController -> controller.AbstractDialogController",
            "sorter/preferredchannelorder/PreferredChannelOrderConfigDialogController -> controller.AbstractDialogController",
            "input/file/FileInput -> util.DialogUtil",
            "output/file/FileOutput -> util.DialogUtil",
            "sorter/preferredchannelorder/PreferredChannelOrderConfigDialogController -> util.DialogUtil",
            "input/fritzbox/FritzboxInputDialogController -> util.PojoValidator",
            "sftp/SftpConnectionDialogController -> util.PojoValidator",
            // used by the configuration dialog of m3uedit too, which has no plugin context
            "enricher/epg/EpgCache -> config.AppConfigPaths",
            "enricher/epg/EpgCache -> i18n.Messages",
            "input/fritzbox/RecentFritzBoxConnections -> config.AppConfigPaths",
            "input/fritzbox/RecentFritzBoxConnections -> config.YamlConfigManager",
            "sftp/RecentSftpServers -> config.AppConfigPaths",
            "sftp/RecentSftpServers -> config.YamlConfigManager");

    @Test
    void testThePluginsUseOnlyTheListedInternals() throws IOException {
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(PLUGINS)) {
            // the plugins themselves are in the sub-packages; the package itself contains the plugin services
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getParent() != null && !path.getParent().equals(PLUGINS)).toList()) {
                String plugin = PLUGINS.relativize(file).toString().replace('\\', '/').replaceAll("\\.java$", "");
                Matcher matcher = INTERNAL.matcher(Files.readString(file));
                while (matcher.find()) {
                    String internal = matcher.group(1);
                    // other built-in plugins (e.g. the shared SFTP support) are no internals of m3uedit
                    if (!internal.startsWith("plugins.")) {
                        used.add(plugin + " -> " + internal);
                    }
                }
            }
        }

        assertThat(used).containsExactlyInAnyOrderElementsOf(ALLOWED);
    }
}

package org.bitpunk.m3uedit.plugins.input.iptvorg;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.InputReadException;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class IptvOrgInputTest {
    private static final IptvOrgPlaylist GERMANY = new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany");
    private static final IptvOrgPlaylist NEWS = new IptvOrgPlaylist(PlaylistGrouping.CATEGORY, "news", "News");
    private static final IptvOrgPlaylist UNKNOWN = new IptvOrgPlaylist(PlaylistGrouping.LANGUAGE, "xyz", "Unknown");
    private static final IptvOrgPlaylist ZDF = new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "ZDF.de", "ZDF");
    private static final IptvOrgPlaylist KIKA = new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "KiKA.de", "KiKA");

    @Test
    void testBasicProperties() {
        IptvOrgInput input = TestPluginSupport.create(IptvOrgInput::new);
        assertThat(input.getName()).isEqualTo("IPTV-org Input");
        assertThat(input.getDescription()).contains("iptv-org");
        assertThat(input.isConfigurable()).isTrue();
        assertThat(input.getSelectedPlaylists()).isEmpty();
        assertThat(input.getSource()).isNull();
        assertThat(PluginStates.enabled(input).get()).isFalse();
    }

    @Test
    void testGetSourceListsPlaylistPaths() {
        IptvOrgInput input = TestPluginSupport.create(IptvOrgInput::new);
        input.setSelectedPlaylists(List.of(GERMANY, NEWS, ZDF));
        assertThat(input.getSource()).isEqualTo("countries/de.m3u, categories/news.m3u, channels/ZDF.de");
    }

    @Test
    void testReadOnlyStreamsOfSelectedChannels() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer()
                .serve("/index.m3u", """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="ZDF.de@SD" group-title="General",ZDF (720p)
                        https://example.com/zdf1.m3u8
                        #EXTINF:-1 tvg-id="3sat.de@SD" group-title="General",3sat
                        https://example.com/3sat.m3u8
                        #EXTINF:-1 tvg-id="KiKA.de@SD" group-title="Kids",KiKA
                        https://example.com/kika.m3u8
                        #EXTINF:-1 tvg-id="ZDF.de@HD" group-title="General",ZDF HD (1080p)
                        https://example.com/zdf2.m3u8
                        """)) {
            IptvOrgInput input = TestPluginSupport.create(IptvOrgInput::new);
            input.setPlaylistBaseUrl(server.getBaseUrl());
            input.setSelectedPlaylists(List.of(ZDF, KIKA));

            List<Entry> entries = input.read().entries();

            assertThat(entries)
                    .extracting(Entry::getChannelUri)
                    .containsExactly("https://example.com/zdf1.m3u8", "https://example.com/kika.m3u8", "https://example.com/zdf2.m3u8");
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void testAMissingPlaylistIsReportedTheOthersAreKept() throws IOException {
        try (IptvOrgTestServer server = new IptvOrgTestServer()
                .serve("/countries/de.m3u", """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="3sat.de@SD" tvg-logo="https://example.com/3sat.png" group-title="General",3sat
                        https://example.com/3sat.m3u8
                        """)
                .serve("/categories/news.m3u", """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="2GB.au@SD" group-title="News",2GB (1080p)
                        https://example.com/2gb.m3u8
                        """)) {
            IptvOrgInput input = TestPluginSupport.create(IptvOrgInput::new);
            input.setPlaylistBaseUrl(server.getBaseUrl());
            input.setSelectedPlaylists(List.of(GERMANY, UNKNOWN, NEWS));

            InputReadException error = catchThrowableOfType(InputReadException.class, input::read);

            assertThat(error).hasMessageContaining(UNKNOWN.getPath()).hasMessageContaining("status-code 404");
            assertThat(error.getEntriesRead())
                    .extracting(Entry::getName, Entry::getChannelUri, Entry::getGroupTitle)
                    .containsExactly(
                            tuple("3sat", "https://example.com/3sat.m3u8", "General"),
                            tuple("2GB (1080p)", "https://example.com/2gb.m3u8", "News"));
        }
    }

    @Test
    void testReadWithoutSelectionReturnsEmptyList() throws Exception {
        IptvOrgInput input = TestPluginSupport.create(IptvOrgInput::new);
        input.setSelectedPlaylists(null);
        assertThat(input.read().entries()).isEmpty();
    }

    @Test
    void testInputsAreComparedByIdentity() {
        IptvOrgInput input1 = TestPluginSupport.create(IptvOrgInput::new);
        IptvOrgInput input2 = TestPluginSupport.create(IptvOrgInput::new);
        int hashCode = input1.hashCode();

        assertThat(input1).isEqualTo(input1).isNotEqualTo(input2);
        input1.setSelectedPlaylists(new ArrayList<>(List.of(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany"))));
        assertThat(input1.hashCode()).isEqualTo(hashCode);
    }

    @Test
    void testTheSettingsAreRestored() {
        IptvOrgInput input = TestPluginSupport.create(IptvOrgInput::new);
        input.setSelectedPlaylists(List.of(GERMANY, ZDF));

        IptvOrgInput restored = TestPluginSupport.create(IptvOrgInput::new);
        restored.setSettings(input.getSettings());

        assertThat(restored.getSettingsType()).isEqualTo(IptvOrgInputSettings.class);
        assertThat(restored.getSettings()).isEqualTo(new IptvOrgInputSettings(List.of(GERMANY, ZDF)));
        assertThat(restored.getSelectedPlaylists()).containsExactly(GERMANY, ZDF);
    }

    @Test
    void testMissingSettingsSelectNoPlaylists() {
        IptvOrgInput input = TestPluginSupport.create(IptvOrgInput::new);
        input.setSelectedPlaylists(List.of(GERMANY));

        input.setSettings(new IptvOrgInputSettings(null));

        assertThat(input.getSelectedPlaylists()).isEmpty();
        assertThat(input.getSource()).isNull();
    }

    @Test
    void testTheSettingsAreStoredInTheProjectFileAsBefore(@TempDir Path tempDir) throws IOException {
        String settings = """
                ---
                selectedPlaylists:
                  - grouping: COUNTRY
                    id: DE
                    name: Germany
                  - grouping: CHANNEL
                    id: ZDF.de
                    name: ZDF
                """;
        Path projectFile = Files.writeString(tempDir.resolve("iptvorg.m3uproj"), """
                ---
                name: iptvorg
                inputs:
                  - type: IptvOrgInput
                    enabled: true
                """ + settings.substring("---\n".length()).indent(4));

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        IptvOrgInput input = (IptvOrgInput) loaded.getInputs().getFirst();
        assertThat(input.getSelectedPlaylists()).containsExactly(GERMANY, ZDF);
        assertThat(ProjectPersisterUtil.persistedState(input)).isEqualTo(settings);
    }
}

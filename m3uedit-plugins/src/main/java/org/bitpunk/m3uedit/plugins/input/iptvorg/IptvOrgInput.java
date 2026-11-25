package org.bitpunk.m3uedit.plugins.input.iptvorg;

import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.InputReadException;
import org.bitpunk.m3uedit.api.Playlist;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.joining;

/**
 * Reads playlists and channels published by iptv-org.
 */
@Slf4j
@PluginType(id = "IptvOrgInput", nameKey = "input.iptvorg.name", texts = "org.bitpunk.m3uedit.plugins.messages")
public class IptvOrgInput implements Input {
    public static final String IPTV_ORG_PLAYLIST_BASE_URL = "https://iptv-org.github.io/iptv/";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private PluginContext context;
    @Getter
    @Setter
    private List<IptvOrgPlaylist> selectedPlaylists = new ArrayList<>();
    /**
     * Not part of the settings, set by tests only.
     */
    @Getter
    @Setter
    private String playlistBaseUrl = IPTV_ORG_PLAYLIST_BASE_URL;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("input.iptvorg.name");
    }

    @Override
    public String getDescription() {
        return context.text("input.iptvorg.description");
    }

    @Override
    public Class<? extends Record> getSettingsType() {
        return IptvOrgInputSettings.class;
    }

    @Override
    public Record getSettings() {
        return new IptvOrgInputSettings(selectedPlaylists);
    }

    @Override
    public void setSettings(Record settings) {
        List<IptvOrgPlaylist> playlists = ((IptvOrgInputSettings) settings).selectedPlaylists();
        selectedPlaylists = playlists == null ? new ArrayList<>() : new ArrayList<>(playlists);
    }

    @Override
    public Playlist read() {
        List<Entry> entriesRead = new ArrayList<>();
        if (selectedPlaylists != null) {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .build();
            Set<String> selectedChannelIds = new HashSet<>();
            List<String> failures = new ArrayList<>();
            for (IptvOrgPlaylist playlist : selectedPlaylists) {
                if (playlist.getGrouping().isChannel()) {
                    selectedChannelIds.add(playlist.getId());
                } else {
                    try {
                        entriesRead.addAll(readEntriesFromPlaylist(client, playlist.getPath()));
                    } catch (IOException e) {
                        failures.add("%s: %s".formatted(playlist.getPath(), e.getMessage()));
                    }
                }
            }
            if (!selectedChannelIds.isEmpty()) {
                try {
                    entriesRead.addAll(readEntriesOfChannels(client, selectedChannelIds));
                } catch (IOException e) {
                    failures.add(context.text("iptvorg.channelsError", String.join(", ", selectedChannelIds), e.getMessage()));
                }
            }
            if (!failures.isEmpty()) {
                // the entries of the other playlists are kept
                throw new InputReadException(String.join("; ", failures), entriesRead);
            }
        }
        return Playlist.of(entriesRead);
    }

    /**
     * Reads the streams of the given channels out of the playlist containing all streams, as iptv-org provides no
     * playlist per channel.
     */
    private List<Entry> readEntriesOfChannels(HttpClient client, Set<String> channelIds) throws IOException {
        log.debug("reading streams of channels {}", channelIds);
        return readEntriesFromPlaylist(client, IptvOrgPlaylist.ALL_STREAMS_PATH).stream()
                .filter(entry -> channelIds.contains(IptvOrgChannels.channelIdOf(entry)))
                .toList();
    }

    private List<Entry> readEntriesFromPlaylist(HttpClient client, String playlistPath) throws IOException {
        URI uri = URI.create(playlistBaseUrl + playlistPath);
        log.debug("reading playlist from {}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    log.warn("Unable to read playlist {}, status-code: {}", uri, response.statusCode());
                    throw new IOException(context.text("iptvorg.statusCode", response.statusCode(), uri));
                }
                return context.readPlaylist(body).entries();
            }
        } catch (IOException e) {
            log.error("Error reading playlist from url {}", uri, e);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(context.text("iptvorg.interrupted", uri), e);
        }
    }

    @Override
    public String getSource() {
        if (selectedPlaylists == null || selectedPlaylists.isEmpty()) {
            return null;
        }
        return selectedPlaylists.stream().map(IptvOrgPlaylist::getSourceName).collect(joining(", "));
    }

    @Override
    public boolean configure(Window owner) {
        log.debug("configure()");
        IptvOrgInputDialogController controller = new IptvOrgInputDialogController(context,
                new IptvOrgApiClient(context), selectedPlaylists);
        DialogPane dialogPane = controller.getDialogPane();
        // @formatter:off
        dialogPane.lookupButton(controller.okButtonType)
                .disableProperty()
                .bind(controller.getNothingSelected());
        // @formatter:on
        Optional<List<IptvOrgPlaylist>> confirmed = controller.showAndWait();
        confirmed.ifPresent(playlists -> selectedPlaylists = playlists);
        return confirmed.isPresent();
    }

    /**
     * Inputs are compared by identity; only a short description is returned here, as the list of playlists may be
     * large.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), context == null ? "" : getName());
    }
}

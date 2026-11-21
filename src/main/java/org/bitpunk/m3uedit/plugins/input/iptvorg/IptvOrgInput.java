package org.bitpunk.m3uedit.plugins.input.iptvorg;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.scene.control.DialogPane;
import lombok.Setter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.ParsingException;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputReadException;
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
import java.util.Set;

import static java.util.stream.Collectors.joining;

@Getter
@Setter
@Slf4j
public class IptvOrgInput extends AbstractInput implements Input {
    public static final String IPTV_ORG_PLAYLIST_BASE_URL = "https://iptv-org.github.io/iptv/";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private List<IptvOrgPlaylist> selectedPlaylists = new ArrayList<>();
    @JsonIgnore
    private String playlistBaseUrl = IPTV_ORG_PLAYLIST_BASE_URL;

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("input.iptvorg.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("input.iptvorg.description");
    }

    @Override
    public List<Entry> read() {
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
                    } catch (IOException | ParsingException e) {
                        failures.add("%s: %s".formatted(playlist.getPath(), e.getMessage()));
                    }
                }
            }
            if (!selectedChannelIds.isEmpty()) {
                try {
                    entriesRead.addAll(readEntriesOfChannels(client, selectedChannelIds));
                } catch (IOException | ParsingException e) {
                    failures.add(Messages.get("iptvorg.channelsError", String.join(", ", selectedChannelIds), e.getMessage()));
                }
            }
            if (!failures.isEmpty()) {
                // the entries of the other playlists are kept
                throw new InputReadException(String.join("; ", failures), entriesRead);
            }
        }
        return entriesRead;
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
                    throw new IOException(Messages.get("iptvorg.statusCode", response.statusCode(), uri));
                }
                return readData(body);
            }
        } catch (IOException e) {
            log.error("Error reading playlist from url {}", uri, e);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(Messages.get("iptvorg.interrupted", uri), e);
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
    public void configure() {
        log.debug("configure()");
        IptvOrgInputDialogController controller = new IptvOrgInputDialogController(new IptvOrgApiClient(), selectedPlaylists);
        DialogPane dialogPane = controller.getDialogPane();
        // @formatter:off
        dialogPane.lookupButton(controller.okButtonType)
                .disableProperty()
                .bind(controller.getNothingSelected());
        // @formatter:on
        controller.showAndWait().ifPresent(playlists -> selectedPlaylists = playlists);
    }
}

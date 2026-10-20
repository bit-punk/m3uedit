package org.bitpunk.m3uedit.plugins.input.iptvorg;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import javafx.scene.control.DialogPane;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class IptvOrgInput extends AbstractInput implements Input {
    public static final String IPTV_ORG_PLAYLIST_BASE_URL = "https://iptv-org.github.io/iptv/";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @XStreamOmitField
    private final String name = "IPTV-org Input";
    @XStreamOmitField
    private final String description = "Reads playlists by country, category, language or region from https://github.com/iptv-org/iptv.";
    private List<IptvOrgPlaylist> selectedPlaylists = new ArrayList<>();
    @XStreamOmitField
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private String playlistBaseUrl = IPTV_ORG_PLAYLIST_BASE_URL;

    @Override
    public List<Entry> read() {
        List<Entry> entriesRead = new ArrayList<>();
        if (selectedPlaylists != null) {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .build();
            for (IptvOrgPlaylist playlist : selectedPlaylists) {
                entriesRead.addAll(readEntriesFromPlaylist(client, playlist));
            }
        }
        return entriesRead;
    }

    private List<Entry> readEntriesFromPlaylist(HttpClient client, IptvOrgPlaylist playlist) {
        // playlistBaseUrl is null after loading a project, as omitted fields are not initialized by XStream
        URI uri = URI.create(Objects.requireNonNullElse(playlistBaseUrl, IPTV_ORG_PLAYLIST_BASE_URL) + playlist.getPath());
        log.debug("reading playlist {} from {}", playlist, uri);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    log.warn("Unable to read playlist {}, status-code: {}", uri, response.statusCode());
                    return List.of();
                }
                return readData(body);
            }
        } catch (IOException e) {
            log.error("Error reading playlist from url {}", uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while reading playlist from url {}", uri, e);
        }
        return List.of();
    }

    @Override
    public String getSource() {
        if (selectedPlaylists == null || selectedPlaylists.isEmpty()) {
            return null;
        }
        return selectedPlaylists.stream().map(IptvOrgPlaylist::getPath).collect(joining(", "));
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

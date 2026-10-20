package org.bitpunk.m3uedit.plugins.input.iptvorg;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the available playlists of a {@link PlaylistGrouping} from the iptv-org API (see https://github.com/iptv-org/api).
 */
@Slf4j
public class IptvOrgApiClient {
    public static final String IPTV_ORG_API_BASE_URL = "https://iptv-org.github.io/api/";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String apiBaseUrl;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();
    private final Map<PlaylistGrouping, List<IptvOrgPlaylist>> cache = new EnumMap<>(PlaylistGrouping.class);

    public IptvOrgApiClient() {
        this(IPTV_ORG_API_BASE_URL);
    }

    public IptvOrgApiClient(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
    }

    public synchronized List<IptvOrgPlaylist> getPlaylists(PlaylistGrouping grouping) throws IOException {
        List<IptvOrgPlaylist> playlists = cache.get(grouping);
        if (playlists == null) {
            playlists = fetchPlaylists(grouping);
            cache.put(grouping, playlists);
        }
        return playlists;
    }

    private List<IptvOrgPlaylist> fetchPlaylists(PlaylistGrouping grouping) throws IOException {
        URI uri = URI.create(apiBaseUrl + grouping.getPath() + ".json");
        log.debug("reading available playlists from {}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Unexpected status-code %d reading %s".formatted(response.statusCode(), uri));
                }
                return parsePlaylists(grouping, body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading " + uri, e);
        }
    }

    List<IptvOrgPlaylist> parsePlaylists(PlaylistGrouping grouping, InputStream json) {
        List<IptvOrgPlaylist> playlists = new ArrayList<>();
        for (JsonNode node : jsonMapper.readTree(json)) {
            String id = node.path(grouping.getIdField()).asString("");
            String name = node.path("name").asString(id);
            if (!id.isBlank()) {
                playlists.add(new IptvOrgPlaylist(grouping, id, name));
            }
        }
        playlists.sort(Comparator.comparing(IptvOrgPlaylist::getName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(playlists);
    }
}

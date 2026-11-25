package org.bitpunk.m3uedit.plugins.input.iptvorg;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.PluginContext;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.bitpunk.m3uedit.plugins.input.iptvorg.IptvOrgInput.IPTV_ORG_PLAYLIST_BASE_URL;

/**
 * Reads the available playlists of a {@link PlaylistGrouping} from the iptv-org API (see https://github.com/iptv-org/api).
 * The available channels are read from the iptv-org playlist containing all streams.
 */
@Slf4j
public class IptvOrgApiClient {
    public static final String IPTV_ORG_API_BASE_URL = "https://iptv-org.github.io/api/";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final PluginContext context;
    private final String apiBaseUrl;
    private final String playlistBaseUrl;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();
    private final Map<PlaylistGrouping, List<IptvOrgPlaylist>> cache = new EnumMap<>(PlaylistGrouping.class);

    /**
     * @param context the context of the input, e.g. for its texts
     */
    public IptvOrgApiClient(PluginContext context) {
        this(context, IPTV_ORG_API_BASE_URL, IPTV_ORG_PLAYLIST_BASE_URL);
    }

    public IptvOrgApiClient(PluginContext context, String apiBaseUrl, String playlistBaseUrl) {
        this.context = context;
        this.apiBaseUrl = apiBaseUrl;
        this.playlistBaseUrl = playlistBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
    }

    public synchronized List<IptvOrgPlaylist> getPlaylists(PlaylistGrouping grouping) throws IOException {
        List<IptvOrgPlaylist> playlists = cache.get(grouping);
        if (playlists == null) {
            playlists = grouping.isChannel() ? fetchChannels() : fetchPlaylists(grouping);
            cache.put(grouping, playlists);
        }
        return playlists;
    }

    private List<IptvOrgPlaylist> fetchPlaylists(PlaylistGrouping grouping) throws IOException {
        try (InputStream json = fetch(URI.create(apiBaseUrl + grouping.getPath() + ".json"))) {
            return parsePlaylists(grouping, json);
        }
    }

    private List<IptvOrgPlaylist> fetchChannels() throws IOException {
        try (InputStream playlist = fetch(URI.create(playlistBaseUrl + IptvOrgPlaylist.ALL_STREAMS_PATH))) {
            return parseChannels(playlist);
        }
    }

    private InputStream fetch(URI uri) throws IOException {
        log.debug("reading available playlists from {}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException(context.text("input.statusCode", response.statusCode(), uri));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(context.text("input.interrupted", uri), e);
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
        return sortedByName(playlists);
    }

    /**
     * Parses the given playlist and returns one entry per channel, using the name of its first stream.
     */
    List<IptvOrgPlaylist> parseChannels(InputStream playlist) throws IOException {
        Map<String, IptvOrgPlaylist> channels = new LinkedHashMap<>();
        for (Entry entry : context.readPlaylist(playlist).entries()) {
            channels.computeIfAbsent(IptvOrgChannels.channelIdOf(entry),
                    id -> new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, id, IptvOrgChannels.channelNameOf(entry)));
        }
        return sortedByName(new ArrayList<>(channels.values()));
    }

    private static List<IptvOrgPlaylist> sortedByName(List<IptvOrgPlaylist> playlists) {
        playlists.sort(Comparator.comparing(IptvOrgPlaylist::getName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(playlists);
    }
}

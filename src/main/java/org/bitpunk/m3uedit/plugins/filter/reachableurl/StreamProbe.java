package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Checks whether a stream can actually be played, not only whether its host accepts connections.
 * <ul>
 *     <li>http(s): a GET-request must answer with a 2xx status (redirects are followed), must not be an html
 *     error-page and must deliver data. HLS-playlists are followed down to the first media-segment.</li>
 *     <li>rtsp: a DESCRIBE-request must be answered with status 200.</li>
 *     <li>file: the file must exist.</li>
 *     <li>other schemes: the host must accept a tcp-connection.</li>
 * </ul>
 * Every single request (connect and read) is bounded by the configured timeout.
 */
@Slf4j
class StreamProbe {
    static final String DEFAULT_USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21";
    static final String VLC_OPT_USER_AGENT = "http-user-agent";
    static final String VLC_OPT_REFERRER = "http-referrer";
    /**
     * Maximum number of http-redirects followed for a single request.
     */
    static final int MAX_REDIRECTS = 5;
    /**
     * Maximum nesting of HLS-playlists (master-playlist -> media-playlist -> segment).
     */
    static final int MAX_PLAYLIST_DEPTH = 3;
    /**
     * Maximum number of bytes read of a playlist.
     */
    static final int MAX_PLAYLIST_BYTES = 256 * 1024;
    private static final String HLS_HEADER = "#EXTM3U";
    private static final Map<String, Integer> DEFAULT_PORTS = Map.of(
            "http", 80,
            "https", 443,
            "rtsp", 554,
            "ftp", 21);

    private final int timeoutMillis;
    private final String userAgent;
    private final String referrer;

    /**
     * @param timeoutMillis connect- and read-timeout of every request in milliseconds
     * @param vlcOpts       the #EXTVLCOPT options of the entry (user-agent, referrer), may be null
     */
    StreamProbe(int timeoutMillis, Map<String, String> vlcOpts) {
        this.timeoutMillis = timeoutMillis;
        Map<String, String> opts = vlcOpts == null ? Map.of() : vlcOpts;
        this.userAgent = nonBlankOrDefault(opts.get(VLC_OPT_USER_AGENT), DEFAULT_USER_AGENT);
        this.referrer = nonBlankOrDefault(opts.get(VLC_OPT_REFERRER), null);
    }

    private static String nonBlankOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    /**
     * @param urlString the url to check
     * @return true if the stream delivers data, false otherwise (also for malformed urls)
     */
    boolean isPlayable(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(urlString.strip());
        } catch (URISyntaxException e) {
            log.warn("Malformed URL '{}': {}", urlString, e.getMessage());
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "file" -> isFilePresent(uri);
            case "http", "https" -> isHttpStreamPlayable(uri, 0);
            case "rtsp" -> isRtspStreamPlayable(uri);
            default -> isHostReachable(uri, scheme);
        };
    }

    private boolean isFilePresent(URI uri) {
        try {
            return Files.exists(Path.of(uri));
        } catch (IllegalArgumentException e) {
            log.debug("Invalid file url '{}': {}", uri, e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------- http

    private boolean isHttpStreamPlayable(URI uri, int playlistDepth) {
        HttpURLConnection connection = null;
        try {
            connection = openFollowingRedirects(uri);
            if (connection == null) {
                return false;
            }
            URI finalUri = connection.getURL().toURI();
            String contentType = String.valueOf(connection.getContentType()).toLowerCase(Locale.ROOT);
            if (contentType.startsWith("text/html")) {
                log.debug("Stream {} delivers an html-page instead of media", finalUri);
                return false;
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] start = in.readNBytes(HLS_HEADER.length());
                if (start.length == 0) {
                    log.debug("Stream {} delivers no data", finalUri);
                    return false;
                }
                if (isHlsPlaylist(contentType, start)) {
                    String playlist = new String(start, StandardCharsets.UTF_8)
                            + new String(in.readNBytes(MAX_PLAYLIST_BYTES), StandardCharsets.UTF_8);
                    return isHlsPlaylistPlayable(finalUri, playlist, playlistDepth);
                }
                return true;
            }
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            log.debug("Stream {} is not playable: {}", uri, e.toString());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * @return the connection after following all redirects, or null if the final response is not successful
     */
    private HttpURLConnection openFollowingRedirects(URI uri) throws IOException, URISyntaxException {
        URI current = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            URLConnection urlConnection = current.toURL().openConnection();
            if (!(urlConnection instanceof HttpURLConnection connection)) {
                return null;
            }
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Accept", "*/*");
            if (referrer != null) {
                connection.setRequestProperty("Referer", referrer);
            }
            int status = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            if (status >= 300 && status < 400 && location != null) {
                connection.disconnect();
                current = current.resolve(location.strip());
                continue;
            }
            if (status < 200 || status >= 300) {
                log.debug("Stream {} answered with status {}", current, status);
                connection.disconnect();
                return null;
            }
            return connection;
        }
        log.debug("Stream {} exceeds the maximum of {} redirects", uri, MAX_REDIRECTS);
        return null;
    }

    private static boolean isHlsPlaylist(String contentType, byte[] start) {
        return contentType.contains("mpegurl")
                || new String(start, StandardCharsets.US_ASCII).equals(HLS_HEADER);
    }

    /**
     * A HLS-playlist is playable if its first entry (variant-stream or media-segment) is playable.
     */
    private boolean isHlsPlaylistPlayable(URI playlistUri, String playlist, int playlistDepth) {
        if (playlistDepth >= MAX_PLAYLIST_DEPTH) {
            log.debug("Playlist {} exceeds the maximum nesting of {}", playlistUri, MAX_PLAYLIST_DEPTH);
            return false;
        }
        String firstEntry = playlist.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .findFirst()
                .orElse(null);
        if (firstEntry == null) {
            log.debug("Playlist {} contains no streams", playlistUri);
            return false;
        }
        URI entryUri = playlistUri.resolve(firstEntry);
        String scheme = String.valueOf(entryUri.getScheme()).toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return isPlayable(entryUri.toString());
        }
        return isHttpStreamPlayable(entryUri, playlistDepth + 1);
    }

    // ---------------------------------------------------------------- rtsp

    private boolean isRtspStreamPlayable(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : DEFAULT_PORTS.get("rtsp");
        if (host == null) {
            log.debug("Cannot determine host of url '{}'", uri);
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            String request = "DESCRIBE " + uri.toASCIIString() + " RTSP/1.0\r\n"
                    + "CSeq: 1\r\n"
                    + "Accept: application/sdp\r\n"
                    + "User-Agent: " + userAgent + "\r\n"
                    + "\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String statusLine = reader.readLine();
            int status = parseRtspStatus(statusLine);
            if (status != 200) {
                log.debug("Stream {} answered DESCRIBE with '{}'", uri, statusLine);
                return false;
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            log.debug("Stream {} is not playable: {}", uri, e.toString());
            return false;
        }
    }

    /**
     * @param statusLine e.g. "RTSP/1.0 200 OK"
     * @return the status-code, or -1 if the line is no valid rtsp status-line
     */
    static int parseRtspStatus(String statusLine) {
        if (statusLine == null || !statusLine.startsWith("RTSP/")) {
            return -1;
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- other

    private boolean isHostReachable(URI uri, String scheme) {
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : DEFAULT_PORTS.getOrDefault(scheme, -1);
        if (host == null || port == -1) {
            log.debug("Cannot determine host and port of url '{}'", uri);
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            log.debug("Host {}:{} is not reachable: {}", host, port, e.getMessage());
            return false;
        }
    }

}

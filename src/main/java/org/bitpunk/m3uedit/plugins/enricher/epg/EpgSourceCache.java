package org.bitpunk.m3uedit.plugins.enricher.epg;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.config.AppConfigPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads EPG sources to a local cache, as they are large (often more than 100 MB) and change only a few times
 * a day. A cached file is used as long as it is younger than the maximum age; afterwards it is downloaded again,
 * conditionally (ETag / Last-Modified), so an unchanged source is not transferred again.
 * <p>
 * If a source cannot be downloaded, the cached file is used (if there is one), and no further attempt is made
 * for a while, so a server being down does not slow down each re-computation of the playlist.
 */
@Slf4j
class EpgSourceCache {
    static final Duration RETRY_DELAY = Duration.ofMinutes(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final String ETAG = "etag";
    private static final String LAST_MODIFIED = "last-modified";

    /**
     * A local copy of a source.
     *
     * @param file    the local file, null if the source has never been downloaded successfully
     * @param warning a problem the user should know about (e.g. the source could not be updated), null if none
     */
    record CachedSource(Path file, String warning) {
    }

    private final Path directory;
    private final HttpClient httpClient;
    private final Clock clock;
    /**
     * The time of the last failed download of a source, to delay the next attempt.
     */
    private final Map<String, Instant> failedAttempts = new ConcurrentHashMap<>();

    EpgSourceCache() {
        this(AppConfigPaths.getBaseDir().resolve("cache").resolve("epg"),
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build(),
                Clock.systemUTC());
    }

    EpgSourceCache(Path directory, HttpClient httpClient, Clock clock) {
        this.directory = directory;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    /**
     * Returns the local copy of a source, downloading it if necessary. Local files ({@code file:} urls) are used
     * directly. Blocks while downloading.
     *
     * @param maxAge the age of a cached file after which it is downloaded again
     */
    CachedSource get(String url, Duration maxAge) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            return new CachedSource(null, "invalid url %s".formatted(url));
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if ("file".equals(scheme)) {
            Path file = Path.of(uri);
            return Files.isReadable(file) ? new CachedSource(file, null)
                    : new CachedSource(null, "%s cannot be read".formatted(file));
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return new CachedSource(null, "unsupported url %s".formatted(url));
        }
        Path file = cacheFileOf(url);
        boolean cached = Files.isRegularFile(file);
        if (cached && !isOlderThan(file, maxAge)) {
            return new CachedSource(file, null);
        }
        Instant failedAttempt = failedAttempts.get(url);
        if (failedAttempt != null && failedAttempt.plus(RETRY_DELAY).isAfter(clock.instant())) {
            return new CachedSource(cached ? file : null, null);
        }
        try {
            download(uri, file, cached);
            failedAttempts.remove(url);
            return new CachedSource(file, null);
        } catch (IOException e) {
            failedAttempts.put(url, clock.instant());
            log.warn("EPG source {} could not be downloaded: {}", url, e.getMessage());
            return cached
                    ? new CachedSource(file, "%s could not be updated (%s), the copy downloaded before is used"
                    .formatted(url, e.getMessage()))
                    : new CachedSource(null, "%s could not be downloaded: %s".formatted(url, e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CachedSource(cached ? file : null, "downloading %s has been interrupted".formatted(url));
        }
    }

    private boolean isOlderThan(Path file, Duration maxAge) {
        try {
            return Files.getLastModifiedTime(file).toInstant().plus(maxAge).isBefore(clock.instant());
        } catch (IOException e) {
            return true;
        }
    }

    private void download(URI uri, Path file, boolean cached) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "m3uedit")
                .GET();
        Path metaFile = metaFileOf(file);
        Properties meta = readMeta(metaFile);
        if (cached) {
            if (meta.getProperty(ETAG) != null) {
                request.header("If-None-Match", meta.getProperty(ETAG));
            }
            if (meta.getProperty(LAST_MODIFIED) != null) {
                request.header("If-Modified-Since", meta.getProperty(LAST_MODIFIED));
            }
        }
        log.info("downloading EPG source {}", uri);
        HttpResponse<InputStream> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() == 304 && cached) {
                log.info("EPG source {} has not changed", uri);
                Files.setLastModifiedTime(file, FileTime.from(clock.instant()));
                return;
            }
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP status " + response.statusCode());
            }
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".part");
            try {
                try (OutputStream outputStream = Files.newOutputStream(temporary)) {
                    body.transferTo(outputStream);
                }
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            Files.setLastModifiedTime(file, FileTime.from(clock.instant()));
        }
        Properties newMeta = new Properties();
        newMeta.setProperty("url", uri.toString());
        response.headers().firstValue("ETag").ifPresent(value -> newMeta.setProperty(ETAG, value));
        response.headers().firstValue("Last-Modified").ifPresent(value -> newMeta.setProperty(LAST_MODIFIED, value));
        try (OutputStream outputStream = Files.newOutputStream(metaFile)) {
            newMeta.store(outputStream, "EPG source");
        }
        log.info("EPG source {} downloaded ({} bytes)", uri, Files.size(file));
    }

    private static Properties readMeta(Path metaFile) {
        Properties meta = new Properties();
        if (Files.isRegularFile(metaFile)) {
            try (InputStream inputStream = Files.newInputStream(metaFile)) {
                meta.load(inputStream);
            } catch (IOException e) {
                log.debug("cannot read {}: {}", metaFile, e.getMessage());
            }
        }
        return meta;
    }

    Path cacheFileOf(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.strip().getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash, 0, 16) + ".xmltv");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path metaFileOf(Path file) {
        return file.resolveSibling(file.getFileName() + ".properties");
    }
}

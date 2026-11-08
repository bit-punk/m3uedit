package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.reachableurl.model.ReachableUrlFilterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Getter
@Setter
public class ReachableUrlFilter extends AbstractUniqueFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ReachableUrlFilter.class);
    public static final String RESOURCE_PATH = "plugin/filter/reachableurl/reachable_url_filter.yaml";
    public static final String CONFIG_FILE_NAME = "reachable_url_filter.yaml";
    /**
     * Maximum number of hosts checked concurrently.
     */
    private static final int MAX_CONCURRENT_CHECKS = 64;
    private static final Map<String, Integer> DEFAULT_PORTS = Map.of(
            "http", 80,
            "https", 443,
            "rtsp", 554,
            "ftp", 21);

    @JsonIgnore
    private final String name = "Reachable URL Filter";
    @JsonIgnore
    private final String description = "Filters for entries with an unreachable URL";

    private transient YamlConfigManager<ReachableUrlFilterConfig> configManager;

    public ReachableUrlFilter() {
        this(null);
    }

    /**
     * @param configManager provides the configuration, the default configuration-file is used if null
     */
    public ReachableUrlFilter(YamlConfigManager<ReachableUrlFilterConfig> configManager) {
        this.configManager = configManager;
    }

    public YamlConfigManager<ReachableUrlFilterConfig> getConfigManager() {
        // lazily created, if none has been passed to the constructor
        if (configManager == null) {
            configManager = new YamlConfigManager<>(AppConfigPaths.getPluginConfigPath(CONFIG_FILE_NAME), RESOURCE_PATH,
                    ReachableUrlFilterConfig.class, ReachableUrlFilterConfig::new);
        }
        return configManager;
    }

    /**
     * Returns the configured connect-timeout. The configuration is re-read on every call,
     * so changes to the configuration-file take effect without restarting the application.
     *
     * @return the configured timeout in milliseconds, or the default if the configured value is not positive
     */
    int getTimeoutMillis() {
        ReachableUrlFilterConfig config = getConfigManager().loadConfig();
        if (config == null) {
            return ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS;
        }
        if (config.getTimeoutMillis() <= 0) {
            // a timeout of 0 would mean "wait forever"
            log.warn("Invalid timeoutMillis {} in {}, using default of {} ms", config.getTimeoutMillis(),
                    getConfigManager().getUserConfigPath(), ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS);
            return ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS;
        }
        return config.getTimeoutMillis();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        // the check is not repeated here, it would block the user interface
        return "no connection to %s".formatted(removed.getChannelUri());
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        int timeoutMillis = getTimeoutMillis();
        log.debug("checking reachability of {} entries using a timeout of {} ms", list.size(), timeoutMillis);
        Semaphore permits = new Semaphore(MAX_CONCURRENT_CHECKS);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> reachable = new ArrayList<>(list.size());
            for (Entry entry : list) {
                reachable.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return isUrlReachable(entry.getChannelUri(), timeoutMillis);
                    } finally {
                        permits.release();
                    }
                }));
            }
            // keep the original order of the entries
            List<Entry> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (getResult(reachable.get(i))) {
                    result.add(list.get(i));
                }
            }
            return result;
        }
    }

    private static boolean getResult(Future<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.warn("Error checking reachability", e.getCause());
            return false;
        }
    }

    /**
     * Checks whether the given url can be reached.
     * Network urls are checked by opening a tcp-connection to host and port, file-urls by checking the file exists.
     *
     * @param urlString     the url to check
     * @param timeoutMillis connect-timeout in milliseconds
     * @return true if the url is reachable, false otherwise (also for malformed urls)
     */
    boolean isUrlReachable(String urlString, int timeoutMillis) {
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
        if ("file".equals(scheme)) {
            return isFileReachable(uri);
        }
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : DEFAULT_PORTS.getOrDefault(scheme, -1);
        if (host == null || port == -1) {
            log.debug("Cannot determine host and port of url '{}'", urlString);
            return false;
        }
        return isHostReachable(host, port, timeoutMillis);
    }

    private boolean isFileReachable(URI uri) {
        try {
            return Files.exists(Path.of(uri));
        } catch (IllegalArgumentException e) {
            log.debug("Invalid file url '{}': {}", uri, e.getMessage());
            return false;
        }
    }

    boolean isHostReachable(String hostName, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostName, port), timeoutMillis);
            return true;
        } catch (Exception e) {
            log.debug("Host {}:{} is not reachable: {}", hostName, port, e.getMessage());
            return false;
        }
    }

    @Override
    public void configure() {
        log.debug("Opening configuration dialog for ReachableUrlFilter");
        new ReachableUrlFilterConfigDialogController(this).showAndWait();
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }
}

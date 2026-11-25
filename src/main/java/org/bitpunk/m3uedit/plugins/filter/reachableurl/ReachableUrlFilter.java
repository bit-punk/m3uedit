package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.plugins.filter.reachableurl.model.ReachableUrlFilterConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Removes entries whose stream cannot be played, see {@link StreamProbe}. Its timeout is read from its
 * configuration-file ({@value #CONFIG_FILE_NAME}); nothing of it is stored in the project-file.
 */
@Slf4j
@PluginType(id = "ReachableUrlFilter", nameKey = "filter.reachableUrl.name", texts = "i18n.messages")
public class ReachableUrlFilter implements Filter {
    public static final String RESOURCE_PATH = "plugin/filter/reachableurl/reachable_url_filter.yaml";
    public static final String CONFIG_FILE_NAME = "reachable_url_filter.yaml";
    /**
     * Maximum number of streams checked concurrently.
     */
    private static final int MAX_CONCURRENT_CHECKS = 64;

    private PluginContext context;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the context passed to {@link #init(PluginContext)}
     */
    public PluginContext getContext() {
        return context;
    }

    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("filter.reachableUrl.name");
    }

    @Override
    public String getDescription() {
        return context.text("filter.reachableUrl.description");
    }

    /**
     * Returns the configured timeout. The configuration is re-read on every call,
     * so changes to the configuration-file take effect without restarting the application.
     *
     * @return the configured timeout in milliseconds, or the default if the configured value is not positive
     */
    int getTimeoutMillis() {
        ReachableUrlFilterConfig config = context.loadConfig(CONFIG_FILE_NAME, ReachableUrlFilterConfig.class,
                RESOURCE_PATH, ReachableUrlFilterConfig::new);
        if (config == null) {
            return ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS;
        }
        if (config.getTimeoutMillis() <= 0) {
            // a timeout of 0 would mean "wait forever"
            log.warn("Invalid timeoutMillis {} in {}, using default of {} ms", config.getTimeoutMillis(),
                    context.configDirectory().resolve(CONFIG_FILE_NAME),
                    ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS);
            return ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS;
        }
        return config.getTimeoutMillis();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        // the check is not repeated here, it would block the user interface
        return context.text("filter.reachableUrl.reason", removed.getChannelUri());
    }

    @Override
    public int order() {
        return ORDER_PROBING;
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        int timeoutMillis = getTimeoutMillis();
        log.debug("checking playability of {} entries using a timeout of {} ms", list.size(), timeoutMillis);
        Semaphore permits = new Semaphore(MAX_CONCURRENT_CHECKS);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> reachable = new ArrayList<>(list.size());
            for (Entry entry : list) {
                reachable.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return isUrlReachable(entry.getChannelUri(), entry.getVlcOpts(), timeoutMillis);
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
     * Checks whether the stream of the given url can be played, see {@link StreamProbe}.
     *
     * @param urlString     the url to check
     * @param timeoutMillis connect- and read-timeout of every request in milliseconds
     * @return true if the url is reachable, false otherwise (also for malformed urls)
     */
    boolean isUrlReachable(String urlString, int timeoutMillis) {
        return isUrlReachable(urlString, null, timeoutMillis);
    }

    /**
     * @param vlcOpts the #EXTVLCOPT options of the entry, used for user-agent and referrer, may be null
     */
    boolean isUrlReachable(String urlString, Map<String, String> vlcOpts, int timeoutMillis) {
        return new StreamProbe(timeoutMillis, vlcOpts).isPlayable(urlString);
    }

    /**
     * Writes the configuration to the configuration-file, used by the next filtering.
     */
    void saveConfig(ReachableUrlFilterConfig config) {
        context.saveConfig(CONFIG_FILE_NAME, config);
    }

    @Override
    public boolean configure(Window owner) {
        log.debug("Opening configuration dialog for ReachableUrlFilter");
        ReachableUrlFilterConfigDialogController dialog = new ReachableUrlFilterConfigDialogController(this);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait().isPresent();
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    /**
     * Plugins are compared by identity, only a short description is returned.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}

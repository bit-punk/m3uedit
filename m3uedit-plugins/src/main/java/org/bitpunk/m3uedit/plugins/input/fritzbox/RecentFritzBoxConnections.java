package org.bitpunk.m3uedit.plugins.input.fritzbox;

import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.RecentFritzBoxConnectionsConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Persistent list of the FritzBox connections whose playlists have been read successfully (most recent first),
 * stored in {@code ~/.m3uedit/plugins/fritzbox_recent_connections.yaml}. A connection is identified by its base url,
 * the streams selected last are kept with it.
 */
public class RecentFritzBoxConnections {
    public static final String CONFIG_FILE_NAME = "fritzbox_recent_connections.yaml";
    public static final int MAX_ENTRIES = 10;
    private final Supplier<RecentFritzBoxConnectionsConfig> loader;
    private final Consumer<RecentFritzBoxConnectionsConfig> saver;

    /**
     * The connections of the user, stored in the configuration directory of the plugins.
     *
     * @param context the context of the FritzBox input
     */
    public RecentFritzBoxConnections(PluginContext context) {
        this.loader = () -> context.loadConfig(CONFIG_FILE_NAME, RecentFritzBoxConnectionsConfig.class,
                RecentFritzBoxConnectionsConfig::new);
        this.saver = config -> context.saveConfig(CONFIG_FILE_NAME, config);
    }

    /**
     * @return the list as data collected by the FritzBox input, which the user can clear in the configuration of
     * m3uedit
     */
    public CollectedData collectedData(PluginContext context) {
        return new CollectedData() {
            @Override
            public String getId() {
                return "org.bitpunk.m3uedit.fritzbox.recentConnections";
            }

            @Override
            public String getName() {
                return context.text("fritzbox.recentConnections.name");
            }

            @Override
            public String describe() {
                return context.text("plugins.collected.entries", getConnections().size());
            }

            @Override
            public boolean isEmpty() {
                return getConnections().isEmpty();
            }

            @Override
            public void clear() {
                RecentFritzBoxConnections.this.clear();
            }
        };
    }

    /**
     * @return the stored connections, most recently used first
     */
    public List<FritzBoxConnection> getConnections() {
        RecentFritzBoxConnectionsConfig config = loader.get();
        if (config == null || config.getConnections() == null) {
            return new ArrayList<>();
        }
        return config.getConnections().stream().filter(Objects::nonNull)
                .filter(connection -> connection.getBaseUrl() != null && !connection.getBaseUrl().isBlank())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Moves the connection to the top of the list (adding it if new, replacing the streams selected before) and
     * keeps at most {@link #MAX_ENTRIES} entries.
     */
    public void recordSuccess(FritzBoxConnection connection) {
        if (connection == null || connection.getBaseUrl() == null || connection.getBaseUrl().isBlank()) {
            return;
        }
        FritzBoxConnection used = copy(connection);
        List<FritzBoxConnection> connections = getConnections();
        connections.removeIf(stored -> isSameTarget(stored, used));
        connections.addFirst(used);
        while (connections.size() > MAX_ENTRIES) {
            connections.removeLast();
        }
        saver.accept(new RecentFritzBoxConnectionsConfig(connections));
    }

    /**
     * Removes all connections from the list.
     */
    public void clear() {
        saver.accept(new RecentFritzBoxConnectionsConfig(new ArrayList<>()));
    }

    /**
     * Removes a connection from the list.
     */
    public void remove(FritzBoxConnection connection) {
        List<FritzBoxConnection> connections = getConnections();
        if (connections.removeIf(stored -> isSameTarget(stored, connection))) {
            saver.accept(new RecentFritzBoxConnectionsConfig(connections));
        }
    }

    /**
     * Base urls differing only by case or a trailing slash are the same.
     */
    static boolean isSameTarget(FritzBoxConnection connection, FritzBoxConnection other) {
        return normalize(connection.getBaseUrl()).equals(normalize(other.getBaseUrl()));
    }

    private static String normalize(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.strip().toLowerCase(Locale.ROOT);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static FritzBoxConnection copy(FritzBoxConnection connection) {
        FritzBoxConnection copy = new FritzBoxConnection();
        copy.setBaseUrl(connection.getBaseUrl().strip());
        copy.setSelectedStreamSources(new ArrayList<>(connection.getSelectedStreamSources() == null ? List.of()
                : connection.getSelectedStreamSources().stream().distinct().toList()));
        return copy;
    }
}

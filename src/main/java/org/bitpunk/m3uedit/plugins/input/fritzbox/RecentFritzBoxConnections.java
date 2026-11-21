package org.bitpunk.m3uedit.plugins.input.fritzbox;

import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.RecentFritzBoxConnectionsConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Persistent list of the FritzBox connections whose playlists have been read successfully (most recent first),
 * stored in {@code ~/.m3uedit/plugins/fritzbox_recent_connections.yaml}. A connection is identified by its base url,
 * the streams selected last are kept with it.
 */
public class RecentFritzBoxConnections {
    public static final String CONFIG_FILE_NAME = "fritzbox_recent_connections.yaml";
    public static final int MAX_ENTRIES = 10;
    private final YamlConfigManager<RecentFritzBoxConnectionsConfig> configManager;

    public RecentFritzBoxConnections() {
        this(AppConfigPaths.getPluginConfigPath(CONFIG_FILE_NAME));
    }

    public RecentFritzBoxConnections(Path configPath) {
        this.configManager = new YamlConfigManager<>(configPath, RecentFritzBoxConnectionsConfig.class,
                RecentFritzBoxConnectionsConfig::new);
    }

    /**
     * @return the stored connections, most recently used first
     */
    public List<FritzBoxConnection> getConnections() {
        RecentFritzBoxConnectionsConfig config = configManager.loadConfig();
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
        configManager.saveConfig(new RecentFritzBoxConnectionsConfig(connections));
    }

    /**
     * Removes all connections from the list.
     */
    public void clear() {
        configManager.saveConfig(new RecentFritzBoxConnectionsConfig(new ArrayList<>()));
    }

    /**
     * Removes a connection from the list.
     */
    public void remove(FritzBoxConnection connection) {
        List<FritzBoxConnection> connections = getConnections();
        if (connections.removeIf(stored -> isSameTarget(stored, connection))) {
            configManager.saveConfig(new RecentFritzBoxConnectionsConfig(connections));
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

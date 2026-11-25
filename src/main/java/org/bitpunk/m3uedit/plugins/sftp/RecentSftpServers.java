package org.bitpunk.m3uedit.plugins.sftp;

import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.plugins.sftp.model.RecentSftpServer;
import org.bitpunk.m3uedit.plugins.sftp.model.RecentSftpServersConfig;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent list of SFTP targets the user communicated with successfully (most recent first),
 * stored in {@code ~/.m3uedit/plugins/sftp_recent_servers.yaml}. Passwords are never stored.
 */
public class RecentSftpServers {
    public static final String CONFIG_FILE_NAME = "sftp_recent_servers.yaml";
    public static final int MAX_ENTRIES = 10;
    private final YamlConfigManager<RecentSftpServersConfig> configManager;

    /**
     * The list of the application (e.g. its configuration dialog), which has no plugin context.
     */
    public RecentSftpServers() {
        this(AppConfigPaths.getPluginConfigPath(CONFIG_FILE_NAME));
    }

    /**
     * The list of the SFTP plugins, in the configuration directory of their context.
     */
    public RecentSftpServers(PluginContext context) {
        this(context.configDirectory().resolve(CONFIG_FILE_NAME));
    }

    public RecentSftpServers(Path configPath) {
        this.configManager = new YamlConfigManager<>(configPath, RecentSftpServersConfig.class, RecentSftpServersConfig::new);
    }

    /**
     * @return the stored targets, most recently used first
     */
    public List<RecentSftpServer> getServers() {
        RecentSftpServersConfig config = configManager.loadConfig();
        return config == null || config.getServers() == null ? new ArrayList<>() : new ArrayList<>(config.getServers());
    }

    /**
     * Moves the target of the connection to the top of the list (adding it if new)
     * and keeps at most {@link #MAX_ENTRIES} entries.
     */
    public void recordSuccess(SftpConnection connection) {
        if (connection == null || connection.getHostName() == null || connection.getHostName().isBlank()) {
            return;
        }
        RecentSftpServer used = RecentSftpServer.of(connection);
        List<RecentSftpServer> servers = getServers();
        servers.removeIf(used::isSameTarget);
        servers.addFirst(used);
        while (servers.size() > MAX_ENTRIES) {
            servers.removeLast();
        }
        configManager.saveConfig(new RecentSftpServersConfig(servers));
    }

    /**
     * Removes all targets from the list.
     */
    public void clear() {
        configManager.saveConfig(new RecentSftpServersConfig(new ArrayList<>()));
    }

    /**
     * Removes a target from the list.
     */
    public void remove(RecentSftpServer server) {
        List<RecentSftpServer> servers = getServers();
        if (servers.removeIf(server::isSameTarget)) {
            configManager.saveConfig(new RecentSftpServersConfig(servers));
        }
    }
}

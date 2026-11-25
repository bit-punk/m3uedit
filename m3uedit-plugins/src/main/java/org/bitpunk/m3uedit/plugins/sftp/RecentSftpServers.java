package org.bitpunk.m3uedit.plugins.sftp;

import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.sftp.model.RecentSftpServer;
import org.bitpunk.m3uedit.plugins.sftp.model.RecentSftpServersConfig;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent list of SFTP targets the user communicated with successfully (most recent first),
 * stored in {@code ~/.m3uedit/plugins/sftp_recent_servers.yaml}. Passwords are never stored.
 */
public class RecentSftpServers {
    public static final String CONFIG_FILE_NAME = "sftp_recent_servers.yaml";
    public static final int MAX_ENTRIES = 10;
    private final PluginContext context;

    /**
     * The list of the SFTP plugins, in the configuration directory of their context.
     */
    public RecentSftpServers(PluginContext context) {
        this.context = context;
    }

    private RecentSftpServersConfig load() {
        return context.loadConfig(CONFIG_FILE_NAME, RecentSftpServersConfig.class, RecentSftpServersConfig::new);
    }

    private void save(RecentSftpServersConfig config) {
        context.saveConfig(CONFIG_FILE_NAME, config);
    }

    /**
     * @return the list as data collected by the SFTP plugins, which the user can clear in the configuration of m3uedit
     */
    public CollectedData collectedData() {
        return new CollectedData() {
            @Override
            public String getId() {
                return "org.bitpunk.m3uedit.sftp.recentServers";
            }

            @Override
            public String getName() {
                return context.text("sftp.recentServers.name");
            }

            @Override
            public String describe() {
                return context.text("plugins.collected.entries", getServers().size());
            }

            @Override
            public boolean isEmpty() {
                return getServers().isEmpty();
            }

            @Override
            public void clear() {
                RecentSftpServers.this.clear();
            }
        };
    }

    /**
     * @return the stored targets, most recently used first
     */
    public List<RecentSftpServer> getServers() {
        RecentSftpServersConfig config = load();
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
        save(new RecentSftpServersConfig(servers));
    }

    /**
     * Removes all targets from the list.
     */
    public void clear() {
        save(new RecentSftpServersConfig(new ArrayList<>()));
    }

    /**
     * Removes a target from the list.
     */
    public void remove(RecentSftpServer server) {
        List<RecentSftpServer> servers = getServers();
        if (servers.removeIf(server::isSameTarget)) {
            save(new RecentSftpServersConfig(servers));
        }
    }
}

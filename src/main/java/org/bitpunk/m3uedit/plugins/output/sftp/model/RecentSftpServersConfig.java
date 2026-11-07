package org.bitpunk.m3uedit.plugins.output.sftp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Content of {@code ~/.m3uedit/plugins/sftp_recent_servers.yaml}; most recently used first.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentSftpServersConfig {
    private List<RecentSftpServer> servers = new ArrayList<>();
}

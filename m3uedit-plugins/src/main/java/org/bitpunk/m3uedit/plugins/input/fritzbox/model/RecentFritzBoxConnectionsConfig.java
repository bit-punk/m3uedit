package org.bitpunk.m3uedit.plugins.input.fritzbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The FritzBox connections read successfully, stored in {@code ~/.m3uedit/plugins/fritzbox_recent_connections.yaml}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentFritzBoxConnectionsConfig {
    /**
     * The connections, the most recently used first.
     */
    private List<FritzBoxConnection> connections = new ArrayList<>();
}

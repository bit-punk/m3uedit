package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.AbstractUniqueFilter;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class ReachableUrlFilter extends AbstractUniqueFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ReachableUrlFilter.class);
    @XStreamOmitField
    private final String name = "Reachable URL Filter";
    @XStreamOmitField
    private final String description = "Filters for entries with an unreachable URL";

    @Override
    public List<Entry> filter(List<Entry> list) {
        return list.stream().filter(entry -> isUrlReachable(entry.getChannelUri(), 10)).toList();
    }

    boolean isUrlReachable(String urlString, int timeout) {
        String host;
        int port;
        try {
            URI uri = new URI(urlString);
            String protocol = uri.getScheme();
            host = uri.getHost();
            port = uri.getPort();

            if (port == -1) {
                if ("http".equals(protocol)) {
                    port = 80;
                } else if ("https".equals(protocol)) {
                    port = 443;
                }
            }

        } catch (URISyntaxException e) {
            log.error("Malformed URL: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return isHostReachable(host, port, timeout);
    }

    boolean isHostReachable(String hostName, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostName, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}

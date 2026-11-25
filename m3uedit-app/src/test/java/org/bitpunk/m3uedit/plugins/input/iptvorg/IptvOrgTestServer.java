package org.bitpunk.m3uedit.plugins.input.iptvorg;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal local http-server serving fixed content per path, used to test the iptv-org input without network access.
 */
class IptvOrgTestServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, String> contents = new ConcurrentHashMap<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    IptvOrgTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            String content = contents.get(exchange.getRequestURI().getPath());
            byte[] body = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(content == null ? 404 : 200, content == null ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    IptvOrgTestServer serve(String path, String content) {
        contents.put(path, content);
        return this;
    }

    String getBaseUrl() {
        return "http://127.0.0.1:%d/".formatted(server.getAddress().getPort());
    }

    int getRequestCount() {
        return requestCount.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

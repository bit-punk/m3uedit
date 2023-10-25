package org.bitpunk.m3uedit.plugins.input;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Data
@Slf4j
public class FritzBoxInput implements Input {

    String source = "http://192.168.178.1/dvb/m3u/tvsd.m3u";

    private final String name = "FritzBox Input";
    private final String description = "Downloads a playlist from a FritzBox.";

    @Override
    public InputStream read() {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(source))
                .build();
        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.debug("Status-code: [{}]", response.statusCode());
        String responseBody = response.body();
        log.debug("response: [{}]", responseBody);
        return new ByteArrayInputStream(responseBody.getBytes());
    }

    @Override
    public void configure() {

    }
}
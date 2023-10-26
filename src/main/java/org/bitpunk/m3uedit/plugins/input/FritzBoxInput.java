package org.bitpunk.m3uedit.plugins.input;

import javafx.scene.control.TextInputDialog;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class FritzBoxInput extends AbstractInput implements Input {

    private final String name = "FritzBox Input";
    private final String description = "Downloads a playlist from a FritzBox.";
    String source = "http://fritz.box//dvb/m3u/tvsd.m3u";

    @Override
    public List<Entry> read() {
        // @formatter:off
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(source))
                .build();
        // @formatter:on
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.debug("Status-code: [{}]", response.statusCode());
        String responseBody = response.body();
        log.debug("response: [{}]", responseBody);
        return readData(new ByteArrayInputStream(responseBody.getBytes()));
    }

    @Override
    public void configure() {
        log.debug("configure()");
        TextInputDialog dialog = new TextInputDialog(source);
        dialog.setTitle("Enter Fritzbox-URL");
        dialog.setHeaderText("Please enter the FritzBox's URL from which we will download the playlist-data.");
        dialog.setContentText("FritzBox-URL:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(url -> {
            log.debug("FritzBox-URL entered: " + url);
            this.source = url;
        });
    }
}
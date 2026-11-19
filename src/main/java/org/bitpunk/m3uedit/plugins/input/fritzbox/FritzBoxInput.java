package org.bitpunk.m3uedit.plugins.input.fritzbox;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.scene.control.DialogPane;
import lombok.Setter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Getter
@Setter
@Slf4j
public class FritzBoxInput extends AbstractInput implements Input {
    @JsonIgnore
    private final String name = "FritzBox Input";
    @JsonIgnore
    private final String description = "Downloads a playlist from a FritzBox.";
    private List<StreamSource> selectedStreamSources;
    private FritzBoxConnection fritzBoxConnection = null;
    /**
     * Used if no connection has been configured (e.g. for projects saved by older versions).
     */
    static final String DEFAULT_BASE_URL = "http://fritz.box/dvb/m3u/";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public List<Entry> read() {
        List<Entry> entriesRead = new ArrayList<>();
        boolean allRead = selectedStreamSources != null && !selectedStreamSources.isEmpty();
        if (selectedStreamSources != null) {
            for (StreamSource selectedStreamSource : selectedStreamSources) {
                URI uri = resolveStreamSourceUri(selectedStreamSource);
                try (InputStream playlist = fetch(uri)) {
                    entriesRead.addAll(readData(playlist));
                } catch (IOException e) {
                    allRead = false;
                    log.error("Error reading playlist {} from FritzBox: {}", uri, e.getMessage(), e);
                }
            }
        }
        // offered by the dialog as recently used connection, only if it works
        if (allRead && fritzBoxConnection != null) {
            FritzBoxConnection used = new FritzBoxConnection();
            used.setBaseUrl(fritzBoxConnection.getBaseUrl());
            used.setSelectedStreamSources(new ArrayList<>(selectedStreamSources));
            createRecentConnections().recordSuccess(used);
        }
        return entriesRead;
    }

    URI resolveStreamSourceUri(StreamSource streamSource) {
        String baseUrl = fritzBoxConnection != null && isNotBlank(fritzBoxConnection.getBaseUrl())
                ? fritzBoxConnection.getBaseUrl().strip()
                : DEFAULT_BASE_URL;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return URI.create(baseUrl).resolve(streamSource.getName());
    }

    @Override
    public String getSource() {
        return fritzBoxConnection != null ? fritzBoxConnection.toString() : null;
    }

    @Override
    public void configure() {
        log.debug("configure()");
        FritzboxInputDialogController fritzboxInputDialogController = new FritzboxInputDialogController();
        DialogPane dialogPane = fritzboxInputDialogController.getDialogPane();
        // @formatter:off
        dialogPane.lookupButton(fritzboxInputDialogController.okButtonType)
                .disableProperty()
                .bind(fritzboxInputDialogController.getInputsFull().not());
        // @formatter:on
        // a cancelled dialog changes nothing
        fritzboxInputDialogController.showAndWait().ifPresent(connection -> {
            fritzBoxConnection = connection;
            selectedStreamSources = new ArrayList<>(connection.getSelectedStreamSources());
        });
    }

    protected RecentFritzBoxConnections createRecentConnections() {
        return new RecentFritzBoxConnections();
    }

    private InputStream fetch(URI uri) throws IOException {
        log.debug("reading playlist from FritzBox: {}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            log.debug("Status-code: [{}]", response.statusCode());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Unexpected status-code %d reading %s".formatted(response.statusCode(), uri));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading " + uri, e);
        }
    }

}

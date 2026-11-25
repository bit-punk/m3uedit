package org.bitpunk.m3uedit.plugins.input.fritzbox;

import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.InputReadException;
import org.bitpunk.m3uedit.api.Playlist;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
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
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Reads the playlists of the tv-streams of a FritzBox (e.g. SD and HD streams).
 */
@Slf4j
@PluginType(id = "FritzBoxInput", nameKey = "input.fritzbox.name", texts = "i18n.messages")
public class FritzBoxInput implements Input {
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
    private PluginContext context;
    @Getter
    @Setter
    private List<StreamSource> selectedStreamSources;
    @Getter
    @Setter
    private FritzBoxConnection fritzBoxConnection = null;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("input.fritzbox.name");
    }

    @Override
    public String getDescription() {
        return context.text("input.fritzbox.description");
    }

    @Override
    public Class<? extends Record> getSettingsType() {
        return FritzBoxInputSettings.class;
    }

    @Override
    public Record getSettings() {
        return new FritzBoxInputSettings(selectedStreamSources, fritzBoxConnection);
    }

    @Override
    public void setSettings(Record settings) {
        FritzBoxInputSettings fritzBoxSettings = (FritzBoxInputSettings) settings;
        // missing values keep the defaults: no streams and no connection (the default base url is used then)
        selectedStreamSources = fritzBoxSettings.selectedStreamSources();
        fritzBoxConnection = fritzBoxSettings.fritzBoxConnection();
    }

    @Override
    public Playlist read() {
        List<Entry> entriesRead = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        if (selectedStreamSources != null) {
            for (StreamSource selectedStreamSource : selectedStreamSources) {
                URI uri = resolveStreamSourceUri(selectedStreamSource);
                try (InputStream playlist = fetch(uri)) {
                    entriesRead.addAll(context.readPlaylist(playlist).entries());
                } catch (IOException e) {
                    log.error("Error reading playlist {} from FritzBox: {}", uri, e.getMessage(), e);
                    failures.add("%s: %s".formatted(selectedStreamSource.getName(), e.getMessage()));
                }
            }
        }
        if (!failures.isEmpty()) {
            // the streams read are kept, e.g. the SD streams if only the HD streams cannot be read
            throw new InputReadException(String.join("; ", failures), entriesRead);
        }
        // offered by the dialog as recently used connection, only if it works
        if (selectedStreamSources != null && !selectedStreamSources.isEmpty() && fritzBoxConnection != null) {
            FritzBoxConnection used = new FritzBoxConnection();
            used.setBaseUrl(fritzBoxConnection.getBaseUrl());
            used.setSelectedStreamSources(new ArrayList<>(selectedStreamSources));
            createRecentConnections().recordSuccess(used);
        }
        return Playlist.of(entriesRead);
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
    public boolean configure(Window owner) {
        log.debug("configure()");
        FritzboxInputDialogController fritzboxInputDialogController = new FritzboxInputDialogController(context);
        // an input configured again starts with its current settings
        if (fritzBoxConnection != null) {
            FritzBoxConnection current = new FritzBoxConnection();
            current.setBaseUrl(fritzBoxConnection.getBaseUrl());
            current.setSelectedStreamSources(selectedStreamSources == null ? List.of() : selectedStreamSources);
            fritzboxInputDialogController.setFritzBoxConnection(current);
        }
        DialogPane dialogPane = fritzboxInputDialogController.getDialogPane();
        // @formatter:off
        dialogPane.lookupButton(fritzboxInputDialogController.okButtonType)
                .disableProperty()
                .bind(fritzboxInputDialogController.getInputsFull().not());
        // @formatter:on
        return applyDialogResult(fritzboxInputDialogController.showAndWait());
    }

    /**
     * @param connection the connection confirmed in the dialog, empty if it has been cancelled: then nothing is
     *                   changed
     * @return true if the connection has been confirmed
     */
    boolean applyDialogResult(Optional<FritzBoxConnection> connection) {
        connection.ifPresent(confirmed -> {
            fritzBoxConnection = confirmed;
            selectedStreamSources = new ArrayList<>(confirmed.getSelectedStreamSources());
        });
        return connection.isPresent();
    }

    protected RecentFritzBoxConnections createRecentConnections() {
        return new RecentFritzBoxConnections(context);
    }

    private InputStream fetch(URI uri) throws IOException {
        log.debug("reading playlist from FritzBox: {}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            log.debug("Status-code: [{}]", response.statusCode());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException(context.text("input.statusCode", response.statusCode(), uri));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(context.text("input.interrupted", uri), e);
        }
    }

    /**
     * Inputs are compared by identity; only a short description is returned here.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), context == null ? "" : getName());
    }
}

package org.bitpunk.m3uedit.plugins.input.url;

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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the playlists of one or more urls.
 */
@Slf4j
@PluginType(id = "UrlInput", nameKey = "input.url.name", texts = "i18n.messages")
public class UrlInput implements Input {

    private static final int TIMEOUT_MILLIS = 20_000;
    private PluginContext context;
    @Getter
    @Setter
    private List<URL> selectedInputUrls;
    @Getter
    @Setter
    private List<URL> urlList = null;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("input.url.name");
    }

    @Override
    public String getDescription() {
        return context.text("input.url.description");
    }

    @Override
    public Class<? extends Record> getSettingsType() {
        return UrlInputSettings.class;
    }

    @Override
    public Record getSettings() {
        return new UrlInputSettings(toStrings(selectedInputUrls), toStrings(urlList));
    }

    @Override
    public void setSettings(Record settings) {
        UrlInputSettings urlInputSettings = (UrlInputSettings) settings;
        selectedInputUrls = toUrls(urlInputSettings.selectedInputUrls());
        urlList = toUrls(urlInputSettings.urlList());
    }

    private static List<String> toStrings(List<URL> urls) {
        return urls == null ? null : urls.stream().filter(Objects::nonNull).map(URL::toString).toList();
    }

    /**
     * @return the urls, without the invalid ones (which are logged); null if there are none (not configured)
     */
    private static List<URL> toUrls(List<String> urls) {
        if (urls == null) {
            return null;
        }
        List<URL> converted = new ArrayList<>();
        for (String url : urls) {
            try {
                converted.add(URI.create(url).toURL());
            } catch (IllegalArgumentException | NullPointerException | MalformedURLException e) {
                log.warn("Ignoring the invalid url {}: {}", url, e.getMessage());
            }
        }
        return converted;
    }

    @Override
    public Playlist read() {
        List<Entry> entriesRead = new ArrayList<>();
        List<String> epgUrls = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        if (selectedInputUrls != null) {
            for (URL url : selectedInputUrls) {
                try {
                    Playlist playlist = readPlaylistFromUrl(url);
                    entriesRead.addAll(playlist.entries());
                    epgUrls.addAll(playlist.epgUrls());
                } catch (IOException e) {
                    log.error("Error reading playlist from url {}", url, e);
                    failures.add("%s: %s".formatted(url, e.getMessage()));
                }
            }
        }
        if (!failures.isEmpty()) {
            // the playlists of the other urls are kept
            throw new InputReadException(String.join("; ", failures), entriesRead);
        }
        return new Playlist(entriesRead, epgUrls);
    }

    private Playlist readPlaylistFromUrl(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        try (InputStream inputStream = connection.getInputStream()) {
            return context.readPlaylist(inputStream);
        }
    }

    @Override
    public String getSource() {
        return urlList == null ? null : urlList.toString();
    }

    @Override
    public boolean configure(Window owner) {
        log.debug("configure()");
        UrlInputDialogController urlInputDialogController = new UrlInputDialogController(context);
        // an input configured again starts with its current urls
        urlInputDialogController.setUrls(selectedInputUrls);
        DialogPane dialogPane = urlInputDialogController.getDialogPane();
        // @formatter:off
        dialogPane.lookupButton(urlInputDialogController.okButtonType)
                .disableProperty()
                .bind(urlInputDialogController.getInputsFull());
        // @formatter:on
        return applyDialogResult(urlInputDialogController.showAndWait());
    }

    /**
     * @param urls the urls confirmed in the dialog, empty if it has been cancelled: then nothing is changed (before,
     *             cancelling removed all urls)
     * @return true if the urls have been confirmed
     */
    boolean applyDialogResult(Optional<List<URL>> urls) {
        urls.ifPresent(confirmed -> {
            urlList = confirmed;
            selectedInputUrls = new ArrayList<>(confirmed);
        });
        return urls.isPresent();
    }

    /**
     * Inputs are compared by identity; only a short description is returned here, as the list of urls may be large.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), context == null ? "" : getName());
    }
}

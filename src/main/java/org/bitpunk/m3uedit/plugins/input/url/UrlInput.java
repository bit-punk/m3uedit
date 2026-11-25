package org.bitpunk.m3uedit.plugins.input.url;

import javafx.scene.control.DialogPane;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.EntryList;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.parser.ParsingException;
import org.bitpunk.m3uedit.plugins.PluginType;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputReadException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Slf4j
@PluginType(id = "UrlInput", nameKey = "input.url.name")
public class UrlInput extends AbstractInput implements Input {

    private static final int TIMEOUT_MILLIS = 20_000;
    private List<URL> selectedInputUrls;
    private List<URL> urlList = null;


    /**
     * @return the name shown to the user; a constant, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("input.url.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("input.url.description");
    }

    @Override
    public List<Entry> read() {
        EntryList entriesRead = new EntryList();
        List<String> epgUrls = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        if (selectedInputUrls != null) {
            for (URL url : selectedInputUrls) {
                try {
                    EntryList entries = readEntriesFromUrl(url);
                    entriesRead.addAll(entries);
                    epgUrls.addAll(entries.getEpgUrls());
                } catch (IOException | ParsingException e) {
                    log.error("Error reading playlist from url {}", url, e);
                    failures.add("%s: %s".formatted(url, e.getMessage()));
                }
            }
        }
        entriesRead.setEpgUrls(epgUrls);
        if (!failures.isEmpty()) {
            // the playlists of the other urls are kept
            throw new InputReadException(String.join("; ", failures), entriesRead);
        }
        return entriesRead;
    }

    private EntryList readEntriesFromUrl(URL url) throws IOException {
        EntryList entries = new EntryList();
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        try (InputStream inputStream = connection.getInputStream()) {
            EntryList parsed = new Parser().parse(inputStream);
            if (parsed != null) {
                entries.addAll(parsed);
                entries.setEpgUrls(parsed.getEpgUrls());
            }
        }
        return entries;
    }

    @Override
    public String getSource() {
        return urlList == null ? null : urlList.toString();
    }

    @Override
    public void configure() {
        log.debug("configure()");
        UrlInputDialogController urlInputDialogController = new UrlInputDialogController();
        // an input configured again starts with its current urls
        urlInputDialogController.setUrls(selectedInputUrls);
        DialogPane dialogPane = urlInputDialogController.getDialogPane();
        // @formatter:off
        dialogPane.lookupButton(urlInputDialogController.okButtonType)
                .disableProperty()
                .bind(urlInputDialogController.getInputsFull());
        // @formatter:on
        applyDialogResult(urlInputDialogController.showAndWait());
    }

    /**
     * @param urls the urls confirmed in the dialog, empty if it has been cancelled: then nothing is changed (before,
     *             cancelling removed all urls)
     */
    void applyDialogResult(Optional<List<URL>> urls) {
        urls.ifPresent(confirmed -> {
            urlList = confirmed;
            selectedInputUrls = new ArrayList<>(confirmed);
        });
    }

}

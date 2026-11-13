package org.bitpunk.m3uedit.plugins.input.url;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.scene.control.DialogPane;
import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.EntryList;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Slf4j
public class UrlInput extends AbstractInput implements Input {

    private static final int TIMEOUT_MILLIS = 20_000;
    private List<URL> selectedInputUrls;
    private List<URL> urlList = null;

    @JsonIgnore
    private final String name = "Url Input";
    @JsonIgnore
    private final String description = "Reads a playlist from a given Url.";

    @Override
    public List<Entry> read() {
        EntryList entriesRead = new EntryList();
        List<String> epgUrls = new ArrayList<>();
        if (selectedInputUrls != null) {
            for (URL url : selectedInputUrls) {
                EntryList entries = readEntriesFromUrl(url);
                entriesRead.addAll(entries);
                epgUrls.addAll(entries.getEpgUrls());
            }
        }
        entriesRead.setEpgUrls(epgUrls);
        return entriesRead;
    }

    private EntryList readEntriesFromUrl(URL url) {
        EntryList entries = new EntryList();
        try {
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
        } catch (IOException e) {
            log.error("Error parsing playlist read from url {}", url, e);
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
        DialogPane dialogPane = urlInputDialogController.getDialogPane();
        // @formatter:off
        dialogPane.lookupButton(urlInputDialogController.okButtonType)
                .disableProperty()
                .bind(urlInputDialogController.getInputsFull());
        // @formatter:on
        urlInputDialogController.showAndWait().ifPresent(con -> urlList = con);
        
        selectedInputUrls = urlInputDialogController.getResult();
    }

}

package org.bitpunk.m3uedit.plugins.input;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import javafx.scene.control.DialogPane;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.plugins.input.url.UrlInputDialogController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class UrlInput extends AbstractInput implements Input{

    private static final Logger log = LoggerFactory.getLogger(UrlInput.class);
    private List<URL> selectedInputUrls;
    private List<URL> urlList = null;

    @XStreamOmitField
    private final String name = "Url Input";
    @XStreamOmitField
    private final String description = "Reads a playlist from a given Url.";

    @Override
    public List<Entry> read() {
        List<Entry> entriesRead = new ArrayList<>();
        if (selectedInputUrls != null) {
            selectedInputUrls.forEach(url -> entriesRead.addAll(readEntriesFromUrl(url)));
        }
        return entriesRead;
    }

    private List<Entry> readEntriesFromUrl(URL url) {
        List<Entry> entries = null;
        try {
            entries = new Parser().parse(url.openStream());
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

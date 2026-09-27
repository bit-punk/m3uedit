package org.bitpunk.m3uedit.plugins.input.fritzbox;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import javafx.scene.control.DialogPane;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.input.AbstractInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class FritzBoxInput extends AbstractInput implements Input {
    @XStreamOmitField
    private final String name = "FritzBox Input";
    @XStreamOmitField
    private final String description = "Downloads a playlist from a FritzBox.";
    private List<StreamSource> selectedStreamSources;
    private FritzBoxConnection fritzBoxConnection = null;
    private static final String BASE_URL = "http://fritz.box//dvb/m3u/";

    @Override
    public List<Entry> read() {
        List<Entry> entriesRead = new ArrayList<>();
        for (StreamSource selectedStreamSource : selectedStreamSources) {
            HttpResponse<String> response = readHttpResponse(BASE_URL + selectedStreamSource.getName());
            log.debug("Status-code: [{}]", response.statusCode());
            String responseBody = response.body();
            log.debug("response: [{}]", responseBody);
            entriesRead.addAll(readData(new ByteArrayInputStream(responseBody.getBytes())));
        }
        return entriesRead;
    }

    @Override
    public String getSource() {
        return fritzBoxConnection.toString();
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
        fritzboxInputDialogController.showAndWait().ifPresent(con -> fritzBoxConnection = con);
        selectedStreamSources = fritzboxInputDialogController.getFritzBoxConnection().getSelectedStreamSources();
    }

    private HttpResponse<String> readHttpResponse(String url) {
        log.debug("reading HttpResponse for url: {}", url);
        // @formatter:off
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();
        // @formatter:on
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

}
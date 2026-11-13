package org.bitpunk.m3uedit.controller;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Edits the urls of the EPG (XMLTV) files written to the playlist header, one url per line.
 * The result is the list of urls; an empty list means the EPG urls of the inputs are used.
 */
public class EpgUrlDialog extends Dialog<List<String>> {
    static final String TITLE = "EPG (programme guide)";
    private static final Set<String> SCHEMES = Set.of("http", "https", "file", "ftp");

    @Getter
    private final TextArea urlTextArea = new TextArea();
    @Getter
    private final Label errorLabel = new Label();
    @Getter
    private final ButtonType okButtonType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);

    /**
     * @param epgUrls      the urls currently set for the project
     * @param inputEpgUrls the urls of the enrichers or found in the headers of the inputs, used if none are set
     */
    public EpgUrlDialog(List<String> epgUrls, List<String> inputEpgUrls) {
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle(TITLE);
        setHeaderText("URLs of the XMLTV files players load the programme guide from");

        Label hint = new Label(inputEpgUrls.isEmpty()
                ? "One URL per line (.xml or .xml.gz). The channels are matched by their tvg-id."
                : "One URL per line (.xml or .xml.gz). The channels are matched by their tvg-id.\n"
                + "Leave empty to use the EPG URLs of the enrichers or inputs:\n" + String.join("\n", inputEpgUrls));
        hint.setWrapText(true);
        urlTextArea.setPromptText("https://example.org/guide.xml.gz");
        urlTextArea.setPrefRowCount(6);
        urlTextArea.setPrefColumnCount(50);
        urlTextArea.setText(String.join("\n", epgUrls));
        VBox.setVgrow(urlTextArea, Priority.ALWAYS);
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);

        VBox content = new VBox(8, hint, urlTextArea, errorLabel);
        if (!inputEpgUrls.isEmpty()) {
            Button copyButton = new Button("Copy these URLs");
            copyButton.setOnAction(event -> urlTextArea.setText(String.join("\n",
                    union(parseUrls(urlTextArea.getText()), inputEpgUrls))));
            content.getChildren().add(2, copyButton);
        }
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(okButtonType, ButtonType.CANCEL);

        Button okButton = (Button) getDialogPane().lookupButton(okButtonType);
        urlTextArea.textProperty().addListener((observable, oldText, newText) -> validate(okButton));
        validate(okButton);
        setResultConverter(buttonType -> buttonType == okButtonType ? parseUrls(urlTextArea.getText()) : null);
    }

    private void validate(Button okButton) {
        List<String> invalid = invalidUrls(urlTextArea.getText());
        okButton.setDisable(!invalid.isEmpty());
        errorLabel.setText(invalid.isEmpty() ? "" : "Not a valid URL: " + String.join(", ", invalid));
    }

    private static List<String> union(List<String> first, List<String> second) {
        Set<String> urls = new LinkedHashSet<>(first);
        urls.addAll(second);
        return List.copyOf(urls);
    }

    /**
     * @return the non-blank lines of the text, stripped and without duplicates
     */
    static List<String> parseUrls(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).distinct().toList();
    }

    /**
     * Commas are not allowed in a url, as the urls are written comma-separated to the header.
     *
     * @return the lines of the text that are no absolute http(s), ftp or file urls
     */
    static List<String> invalidUrls(String text) {
        return parseUrls(text).stream().filter(url -> !isValidUrl(url)).collect(Collectors.toList());
    }

    private static boolean isValidUrl(String url) {
        if (url.contains(",") || url.contains("\"")) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return uri.getScheme() != null && SCHEMES.contains(uri.getScheme().toLowerCase())
                    && (uri.getHost() != null || "file".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}

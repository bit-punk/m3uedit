package org.bitpunk.m3uedit.plugins.input.url;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.support.PluginDialogController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

@Slf4j
public class UrlInputDialogController extends PluginDialogController<List<URL>> implements Initializable {
    public static final String FXML_PLUGIN_INPUT_URL = "plugin/input/url/UrlInputDialog.fxml";
    private final PluginContext context;
    @Getter
    private final List<URL> urlList = new ArrayList<>();
    @FXML
    public ButtonType okButtonType;
    @FXML
    TextField urlTextField;
    @FXML
    ListView<URL> selectedUrlListView;
    @Getter
    private BooleanBinding inputsFull;
    @FXML
    private Button addUrlButton;

    /**
     * @param context the context of the input, e.g. for its texts
     */
    public UrlInputDialogController(PluginContext context) {
        // set before the FXML is loaded, which calls initialize()
        this.context = context;
        super(context, FXML_PLUGIN_INPUT_URL, context.text("url.dialog.title"));
        setResultConverter(getURIResultConverter());
        addUrlButton.setOnAction(actionEvent -> addNewUrl());
        urlTextField.setOnAction(actionEvent -> addNewUrl());
    }

    private void deleteNamedItemsFromTableView(ObservableList<URL> deletePlugins, ListView<URL> urlListView) {
        if (deleteNamedItemsDialog(deletePlugins)) {
            urlListView.getItems().removeAll(deletePlugins);
        }
    }

    private boolean deleteNamedItemsDialog(ObservableList<URL> deleteItems) {
        Alert dialog = new Alert(Alert.AlertType.WARNING);
        dialog.setTitle(context.text("url.delete.title"));
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(377, 270);
        dialog.setHeaderText(context.text("url.delete.title"));
        String deleteConfirmMessage = context.text("url.delete.confirm", deleteItems.size(), formatDeleteUrls(deleteItems));
        dialog.setContentText(deleteConfirmMessage);

        ButtonType buttonYes = new ButtonType(context.text("common.yes"));
        ButtonType buttonNo = new ButtonType(context.text("common.no"), ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(buttonYes, buttonNo);
        Optional<ButtonType> selectedOption = dialog.showAndWait();

        return selectedOption.isPresent() && selectedOption.get() == buttonYes;
    }

    private static String formatDeleteUrls(ObservableList<URL> deleteItems) {
        final int MAX_LENGTH = 30;
        StringBuilder sb = new StringBuilder();
        for (URL url : deleteItems) {
            String urlString = url.toString();
            if (urlString.length() > MAX_LENGTH) {
                int excess = urlString.length() - MAX_LENGTH;
                int cutFrom = MAX_LENGTH / 2;
                int cutTo = cutFrom + excess + 3; // For '...'
                urlString = urlString.substring(0, cutFrom) + "..." + urlString.substring(cutTo);
            }
            sb.append("• ").append(urlString).append("\n");
        }
        return sb.toString();
    }

    /**
     * Shows the urls of an input configured again.
     */
    public void setUrls(List<URL> urls) {
        selectedUrlListView.getItems().setAll(urls == null ? List.of() : urls);
    }

    private void addNewUrl() {
        String newUrlText = urlTextField.getText();
        log.debug("Adding new url: {}", newUrlText);
        String validationMessage = validateUrl(newUrlText);
        if (validationMessage.isEmpty()) {
            try {
                URL newUrl =  URI.create(newUrlText).toURL();
                unmarkTextField(urlTextField);
                selectedUrlListView.getItems().add(newUrl);
                urlTextField.clear();
            } catch (MalformedURLException e) {
                log.error("Unable to create an URL from the input-string '{}': {}", newUrlText, e.getMessage());
                markTextFieldInvalid(urlTextField, context.text("url.invalid"));
            }
        } else markTextFieldInvalid(urlTextField, validationMessage);
    }

    private String validateUrl(String url) {
        String message = "";

        if (url == null || url.isEmpty()) {
            return context.text("url.empty");
        }
        if (selectedUrlListView.getItems().stream().anyMatch(urlItem -> urlItem.toString().equals(url))) {
            return context.text("url.duplicate");
        }
        URL playListUrl;
        try {
            playListUrl = URI.create(url).toURL();
        } catch (MalformedURLException e) {
           return e.getMessage();
        }
        if (!isUrlPlayList(playListUrl)) {
            return context.text("url.noPlaylist");
        }
        return message;
    }

    private boolean isUrlPlayList(URL playListUrl) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(playListUrl.openStream()))) {
            String inputLine;
            if ((inputLine = in.readLine()) != null) {
                return inputLine.trim().equalsIgnoreCase("#EXTM3U");
            }
        } catch (IOException e) {
            log.warn("Could not read playlist from {}: {}", playListUrl, e.getMessage());
        }
        return false;
    }

    private void markTextFieldInvalid(TextField textField, String validationMessage) {
        textField.setStyle(INVALID_INPUT_STYLE);
        textField.setTooltip(new Tooltip(validationMessage));
    }

    private void unmarkTextField(TextField textField) {
        textField.setStyle(null);
        textField.setTooltip(null);
    }

    private Callback<ButtonType, List<URL>> getURIResultConverter() {
        return buttonType -> {
            List<URL> inputUrlList = null;
            if (Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed");
                inputUrlList = createUrlListFromInput();
            }
            log.debug("returning result: {}", inputUrlList);
            return inputUrlList;
        };
    }

    private List<URL> createUrlListFromInput() {
        List<URL> urlList;
        urlList = new ArrayList<>(selectedUrlListView.getItems());
        return urlList;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initInputTableView();
        initializeBinding();
    }

    private void initInputTableView() {
        selectedUrlListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        selectedUrlListView.setOnKeyPressed(this::deleteKeyListenerTableView);
    }

    public void deleteKeyListenerTableView(KeyEvent keyEvent) {
        if (keyEvent.getCode().equals(KeyCode.DELETE)) {
            final ObservableList<URL> selectedUrls = selectedUrlListView.getSelectionModel().getSelectedItems();
            deleteNamedItemsFromTableView(selectedUrls, selectedUrlListView);
            keyEvent.consume();
        }
    }

    private void initializeBinding() {
        inputsFull = Bindings.isEmpty(selectedUrlListView.getItems());
    }

}
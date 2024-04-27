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
import lombok.Getter;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class UrlInputDialogController extends AbstractDialogController<List<URL>> implements Initializable {
    public static final String FXML_PLUGIN_INPUT_URL = "plugin/input/url/UrlInputDialog.fxml";
    public static final String URL_DIALOG_TITLE = "Enter Url(s) to read entries from";
    private static final Logger log = LoggerFactory.getLogger(UrlInputDialogController.class);
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

    public UrlInputDialogController() {
        super(FXML_PLUGIN_INPUT_URL, URL_DIALOG_TITLE);
        setResultConverter(getURIResultConverter());
        addUrlButton.setOnAction(actionEvent -> addNewUrl());
        urlTextField.setOnAction(actionEvent -> addNewUrl());
    }

    private static void deleteNamedItemsFromTableView(ObservableList<URL> deletePlugins, ListView<URL> urlListView) {
        if (deleteNamedItemsDialog(deletePlugins)) {
            urlListView.getItems().removeAll(deletePlugins);
        }
    }

    private static boolean deleteNamedItemsDialog(ObservableList<URL> deleteItems) {
        Alert dialog = new Alert(Alert.AlertType.WARNING);
        dialog.setTitle("Delete Item");
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(377, 270);
        dialog.setHeaderText("Delete Item");
        String deleteConfirmMessage = "Sure you want to delete %d items:%n%n%s".formatted(deleteItems.size(), formatDeleteUrls(deleteItems));
        dialog.setContentText(deleteConfirmMessage);

        ButtonType buttonYes = new ButtonType("Yes");
        ButtonType buttonNo = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

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

    private void addNewUrl() {
        String newUrlText = urlTextField.getText();
        log.debug("Adding new url: {}", newUrlText);
        String validationMessage = validateUrl(newUrlText);
        if (validationMessage.equals("")) {
            try {
                URL newUrl = new URL(newUrlText);
                unmarkTextField(urlTextField);
                selectedUrlListView.getItems().add(newUrl);
                urlTextField.clear();
            } catch (MalformedURLException e) {
                log.error("Unable to create an URL from the input-string '{}': {}", newUrlText, e.getMessage());
                markTextFieldInvalid(urlTextField, "invalid Url provided");
            }
        } else markTextFieldInvalid(urlTextField, validationMessage);
    }

    private String validateUrl(String url) {
        String message = "";

        if (url == null || url.isEmpty()) {
            return "URL cannot be empty";
        }
        if (selectedUrlListView.getItems().stream().anyMatch(urlItem -> urlItem.toString().equals(url))) {
            return "Url already added";
        }
        URL playListUrl = null;
        try {
            playListUrl = new URL(url);
        } catch (MalformedURLException e) {
           return e.getMessage();
        }
        if (!isUrlPlayList(playListUrl)) {
            return "Url does not contain any playlist";
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
            //Handle Exception
            e.printStackTrace();
        }
        return false;
    }

    private void markTextFieldInvalid(TextField textField, String validationMessage) {
        textField.setStyle(CSS_INVALID_TEXT_FIELD_INPUT);
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
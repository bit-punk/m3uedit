package org.bitpunk.m3uedit.plugins.input.url;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Callback;
import lombok.Getter;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

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

    private void addNewUrl() {
        String newUrlText = urlTextField.getText();
        log.debug("Adding new url: {}", newUrlText);
        try {
            URL newUrl = new URL(newUrlText);
            unmarkTextField(urlTextField);
            selectedUrlListView.getItems().add(newUrl);
            urlTextField.clear();
        } catch (MalformedURLException e) {
            log.error("Unable to create an URL from the input-string '{}': {}", newUrlText, e.getMessage());
            markTextFieldInvalid(urlTextField);
        }

    }

    private void markTextFieldInvalid(TextField textField) {
        textField.setStyle(CSS_INVALID_TEXT_FIELD_INPUT);
        textField.setTooltip(new Tooltip("invalid Url provided"));
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
        initializeBinding();
    }

    private void initializeBinding() {
        inputsFull = Bindings.isEmpty(selectedUrlListView.getItems());
    }

}
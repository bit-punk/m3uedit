package org.bitpunk.m3uedit.controller;

import org.bitpunk.m3uedit.util.ChannelLogos;
import javafx.util.Duration;
import javafx.scene.image.ImageView;
import javafx.animation.PauseTransition;
import lombok.extern.slf4j.Slf4j;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.util.converter.IntegerStringConverter;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.api.Entry;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

@Slf4j
public class EntryDialogController<T> extends AbstractDialogController<T> {
    public static final String FXML_ENTRY_DIALOG = "fxml/EntryDialog.fxml";

    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField indexTextField;
    @FXML
    private Label indexLabel;
    @FXML
    private HBox indexHBox;
    @FXML
    private Label maxIndexLabel;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField durationTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField groupTitleTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField tvgIdTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField tvgNameTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField tvgLogoTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField tvgShiftTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField radioTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField channelUriTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField nameTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private MapEditorController mapEditorController;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private ButtonType okButtonType;
    @FXML
    private ImageView logoPreview;
    /**
     * Shows the logo once its URL has not changed for a moment, not for each character typed. Created by
     * {@link #initialize}, which the FXML loader calls before the fields of this class are initialized, so the field
     * must not have an initializer.
     */
    private PauseTransition logoPreviewDelay;
    private BooleanBinding isInputValid;

    public EntryDialogController() {
        super(FXML_ENTRY_DIALOG, Messages.get("entryDialog.title.add"));
        setResultConverter();
        disableOkButtonOnInvalidInput();
        setOnShowing(this::requestFocus);
        setIndexVisible(false);
    }

    public EntryDialogController(Entry entry, int size) {
        super(FXML_ENTRY_DIALOG, Messages.get("entryDialog.title.edit"));
        setResultConverter();
        disableOkButtonOnInvalidInput();
        setOnShowing(this::requestFocus);
        setEntry(entry);
        setMaxIndexLabel(size);
        setIndexVisible(true);
    }

    private void setResultConverter() {
        setResultConverter(buttonType -> {
            if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                return null;
            }
            return (T) getEntry();
        });
    }

    private void disableOkButtonOnInvalidInput() {
        getDialogPane().lookupButton(okButtonType).disableProperty().bind(isInputValid.not());
    }

    private void requestFocus(DialogEvent dialogEvent) {
        Platform.runLater(() -> nameTextField.requestFocus());
    }

    private Entry getEntry() {
        Entry entry = new Entry();
        entry.setIndex(getIntegerFieldValue(indexTextField));
        entry.setDuration(durationTextField.getText());
        entry.setGroupTitle(groupTitleTextField.getText());
        entry.setTvgId(tvgIdTextField.getText());
        entry.setTvgName(tvgNameTextField.getText());
        entry.setTvgLogo(tvgLogoTextField.getText());
        entry.setTvgShift(tvgShiftTextField.getText());
        entry.setRadio(radioTextField.getText());
        entry.setChannelUri(channelUriTextField.getText());
        entry.setName(nameTextField.getText());
        entry.setVlcOpts(mapEditorController.getMap());
        return entry;
    }

    public void setEntry(Entry entry) {
        setIntegerFieldValue(indexTextField, entry.getIndex());
        durationTextField.setText(entry.getDuration());
        groupTitleTextField.setText(entry.getGroupTitle());
        tvgIdTextField.setText(entry.getTvgId());
        tvgNameTextField.setText(entry.getTvgName());
        tvgLogoTextField.setText(entry.getTvgLogo());
        tvgShiftTextField.setText(entry.getTvgShift());
        radioTextField.setText(entry.getRadio());
        channelUriTextField.setText(entry.getChannelUri());
        nameTextField.setText(entry.getName());
        mapEditorController.setMap(entry.getVlcOpts());
        // without delay when the dialog is opened
        logoPreviewDelay.stop();
        updateLogoPreview();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeBinding();
        initKeyEventHandler();
        initLogoPreview();
        initIntegerField(indexTextField,0);
    }

    private void initIntegerField(TextField textField, int defaultValue) {
        TextFormatter<Integer> formatter = new TextFormatter<>(
                new IntegerStringConverter(),
                defaultValue,
                c -> {
                    if (c.getControlNewText().matches("-?\\d*")) {
                        return c;
                    }
                    return null;
                }
        );
        textField.setTextFormatter(formatter);
    }

    private void initLogoPreview() {
        logoPreviewDelay = new PauseTransition(Duration.millis(400));
        logoPreviewDelay.setOnFinished(event -> updateLogoPreview());
        tvgLogoTextField.textProperty().addListener((observable, oldUrl, url) -> logoPreviewDelay.playFromStart());
    }

    private void updateLogoPreview() {
        logoPreview.setImage(ChannelLogos.get(tvgLogoTextField.getText(), logoPreview.getFitWidth(),
                logoPreview.getFitHeight()));
    }

    ImageView logoPreview() {
        return logoPreview;
    }

    private void initializeBinding() {
        isInputValid = Bindings.createBooleanBinding(
                () -> isValidBean(getEntry()), 
                indexTextField.textProperty(),
                durationTextField.textProperty(),
                groupTitleTextField.textProperty(), 
                tvgIdTextField.textProperty(), 
                tvgNameTextField.textProperty(), 
                tvgLogoTextField.textProperty(), 
                tvgShiftTextField.textProperty(), 
                radioTextField.textProperty(), 
                channelUriTextField.textProperty(), 
                nameTextField.textProperty());
    }

    private void initKeyEventHandler() {
        Scene scene = getDialogPane().getScene();
        scene.addEventHandler(KeyEvent.KEY_RELEASED, (KeyEvent event) -> {
            if (KeyCode.ESCAPE == event.getCode()) {
                close();
            }
        });
    }

    private boolean isValidBean(Entry entry) {
        Map<String, String> errors = validate(entry, indexHBox.isVisible());
        displayValidationErrors(errors);
        log.trace("Validation-errors found: {}", errors);
        return errors.isEmpty();
    }

    /**
     * Checks the values of an entry edited by the user.
     *
     * @param withPosition false for a new entry, which is added at the end of the playlist and has no position yet
     * @return the message of each invalid field (in the language of the user interface) by the name of the field;
     * empty if all values are valid
     */
    static Map<String, String> validate(Entry entry, boolean withPosition) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (entry.getName() == null || entry.getName().isEmpty()) {
            errors.put("name", Messages.get("entryDialog.invalid.name"));
        }
        if (entry.getChannelUri() != null && !entry.getChannelUri().matches(Entry.REGEX_URL)) {
            errors.put("channelUri", Messages.get("entryDialog.invalid.url"));
        }
        if (entry.getDuration() != null && !entry.getDuration().matches(Entry.REGEX_NUMERIC)) {
            errors.put("duration", Messages.get("entryDialog.invalid.duration"));
        }
        if (withPosition && entry.getIndex() < 1) {
            errors.put("index", Messages.get("entryDialog.invalid.index"));
        }
        return errors;
    }

    private void displayValidationErrors(Map<String, String> errors) {
        resetTextFields();
        errors.forEach((fieldName, errorMessage) -> {
            switch (fieldName) {
                case "index" -> markFieldInvalid(indexTextField, errorMessage);
                case "duration" -> markFieldInvalid(durationTextField, errorMessage);
                case "channelUri" -> markFieldInvalid(channelUriTextField, errorMessage);
                case "name" -> markFieldInvalid(nameTextField, errorMessage);
                default -> throw new IllegalStateException("Unexpected value: " + fieldName);
            }
        });
    }

    protected int getIntegerFieldValue(TextField textField) {
        String text = textField.getText();
        if (text == null || text.isBlank() || "-".equals(text)) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected void setIntegerFieldValue(TextField textField, int value) {
        if (textField.getTextFormatter() != null) {
            // Update via formatter to ensure consistency
            @SuppressWarnings("unchecked")
            TextFormatter<Object> formatter = (TextFormatter<Object>) textField.getTextFormatter();
            formatter.setValue(value);
        } else {
            textField.setText(String.valueOf(value));
        }
    }

    private void setMaxIndexLabel(int maxIndex) {
        maxIndexLabel.setText(Messages.get("entryDialog.position.max", maxIndex));
    }

    private void setIndexVisible(boolean visible) {
        indexLabel.setVisible(visible);
        indexLabel.setManaged(visible);
        indexHBox.setVisible(visible);
        indexHBox.setManaged(visible);
        // the position is only validated while it is shown
        isInputValid.invalidate();
    }

    private void resetTextFields() {
        resetTextField(indexTextField);
        resetTextField(durationTextField);
        resetTextField(groupTitleTextField);
        resetTextField(tvgIdTextField);
        resetTextField(tvgNameTextField);
        resetTextField(tvgLogoTextField);
        resetTextField(tvgShiftTextField);
        resetTextField(radioTextField);
        resetTextField(channelUriTextField);
        resetTextField(nameTextField);
    }

}
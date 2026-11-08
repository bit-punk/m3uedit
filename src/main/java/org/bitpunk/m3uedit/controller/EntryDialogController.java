package org.bitpunk.m3uedit.controller;

import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolation;
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
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.PojoValidator;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

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
    private BooleanBinding isInputValid;

    public EntryDialogController() {
        super(FXML_ENTRY_DIALOG, "Add new Entry");
        setResultConverter();
        disableOkButtonOnInvalidInput();
        setOnShowing(this::requestFocus);
        setIndexVisible(false);
    }

    public EntryDialogController(Entry entry, int size) {
        super(FXML_ENTRY_DIALOG, "Edit Entry");
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
        Platform.runLater(() -> durationTextField.requestFocus());
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
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeBinding();
        initKeyEventHandler();
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
        PojoValidator<Entry> validator = new PojoValidator<>();
        Set<ConstraintViolation<Entry>> errors = validator.validate(entry);
        displayValidationErrors(errors);
        log.trace("Validation-errors found: {}", errors);
        return errors.isEmpty();
    }

    private void displayValidationErrors(Set<ConstraintViolation<Entry>> errors) {
        resetTextFields();
        for (ConstraintViolation<Entry> error : errors) {
            String fieldName = error.getPropertyPath().toString();
            String errorMessage = error.getMessage();
            log.trace("Field: {} Message: {}", fieldName, errorMessage);
            switch (fieldName) {
                case "duration" -> markFieldInvalid(durationTextField, errorMessage);
                case "groupTitle" -> markFieldInvalid(groupTitleTextField, errorMessage);
                case "tvgId" -> markFieldInvalid(tvgIdTextField, errorMessage);
                case "tvgName" -> markFieldInvalid(tvgNameTextField, errorMessage);
                case "tvgLogo" -> markFieldInvalid(tvgLogoTextField, errorMessage);
                case "tvgShift" -> markFieldInvalid(tvgShiftTextField, errorMessage);
                case "radio" -> markFieldInvalid(radioTextField, errorMessage);
                case "channelUri" -> markFieldInvalid(channelUriTextField, errorMessage);
                case "name" -> markFieldInvalid(nameTextField, errorMessage);
                default -> throw new IllegalStateException("Unexpected value: " + fieldName);
            }
        }
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
        maxIndexLabel.setText(" / " + maxIndex);
    }

    private void setIndexVisible(boolean visible) {
        indexLabel.setVisible(visible);
        indexLabel.setManaged(visible);
        indexHBox.setVisible(visible);
        indexHBox.setManaged(visible);
    }

    private void resetTextFields() {
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
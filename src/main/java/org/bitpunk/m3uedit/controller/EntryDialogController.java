package org.bitpunk.m3uedit.controller;

import jakarta.validation.ConstraintViolation;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.PojoValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

public class EntryDialogController<T> extends AbstractDialogController<T> {
    public static final String FXML_ENTRY_DIALOG = "fxml/EntryDialog.fxml";
    private static final Logger log = LoggerFactory.getLogger(EntryDialogController.class);

    @FXML
    public TextField durationTextField;
    @FXML
    public TextField groupTitleTextField;
    @FXML
    public TextField tvgIdTextField;
    @FXML
    public TextField tvgNameTextField;
    @FXML
    public TextField tvgLogoTextField;
    @FXML
    public TextField tvgShiftTextField;
    @FXML
    public TextField radioTextField;
    @FXML
    public TextField channelUriTextField;
    @FXML
    public TextField nameTextField;
    @FXML
    public MapEditorController mapEditorController;
    @FXML
    ButtonType okButtonType;
    @Getter
    private BooleanBinding inputsValid;

    public EntryDialogController() {

        super(FXML_ENTRY_DIALOG, "Edit Entry");
        setResultConverter(buttonType -> {
            if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                return null;
            }
            return (T) getEntry();
        });

        getDialogPane().lookupButton(okButtonType).disableProperty().bind(getInputsValid().not());

        setOnShowing(dialogEvent -> Platform.runLater(() -> durationTextField.requestFocus()));
    }

    private Entry getEntry() {
        Entry entry = new Entry();
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
    }

    private void initializeBinding() {
        inputsValid = new BooleanBinding() {
            {
                // @formatter:off
                bind(durationTextField.textProperty(),
                        groupTitleTextField.textProperty(),
                        tvgIdTextField.textProperty(),
                        tvgNameTextField.textProperty(),
                        tvgLogoTextField.textProperty(),
                        tvgShiftTextField.textProperty(),
                        radioTextField.textProperty(),
                        channelUriTextField.textProperty(),
                        nameTextField.textProperty());
                // @formatter:on
            }

            @Override
            protected boolean computeValue() {
                return isValidBean(getEntry());
            }
        };
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
        log.debug("Validation-errors found: {}", errors);
        return errors.isEmpty();
    }

    private void displayValidationErrors(Set<ConstraintViolation<Entry>> errors) {
        resetTextFields();
        for (ConstraintViolation<Entry> error : errors) {
            String fieldName = error.getPropertyPath().toString();
            String errorMessage = error.getMessage();
            log.debug("Field: {} Message: {}", fieldName, errorMessage);
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
            }
        }
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
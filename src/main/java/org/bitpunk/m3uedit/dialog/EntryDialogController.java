package org.bitpunk.m3uedit.dialog;

import jakarta.validation.ConstraintViolation;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Modality;
import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.PojoValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

public class EntryDialogController extends Dialog<Entry> implements Initializable {
    public static final String CSS_INVALID_TEXTFIELD_INPUT = "-fx-text-box-border: red; -fx-focus-color: red";
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
    TableView vlcOptionsTableView;
    @FXML
    ButtonType okButtonType;
    @Getter
    private BooleanBinding inputsValid;

    public EntryDialogController() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getClassLoader().getResource("dialog/EntryDialog.fxml"));
            loader.setController(this);

            DialogPane dialogPane = loader.load();

            initModality(Modality.APPLICATION_MODAL);

            setResizable(true);
            setTitle("Edit Entry");
            setDialogPane(dialogPane);
            setResultConverter(buttonType -> {
                if (!Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                    return null;
                }
                return createEntryFromInput();
            });

            dialogPane.lookupButton(okButtonType)
                    .disableProperty()
                    .bind(getInputsValid().not());

            setOnShowing(dialogEvent -> Platform.runLater(() -> durationTextField.requestFocus()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Entry createEntryFromInput() {
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
        // ToDo set VlcOptions
//        entry.setVlcOpts(vlcOptionsTableView.getItems());
        return entry;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeBinding();
    }

    private void initializeBinding() {
        inputsValid = new BooleanBinding() {
            {
                bind(durationTextField.textProperty(),
                        groupTitleTextField.textProperty(),
                        tvgIdTextField.textProperty(),
                        tvgNameTextField.textProperty(),
                        tvgLogoTextField.textProperty(),
                        tvgShiftTextField.textProperty(),
                        radioTextField.textProperty(),
                        channelUriTextField.textProperty(),
                        nameTextField.textProperty());
                // Todo validate VlcOpts ??
            }

            @Override
            protected boolean computeValue() {
                return isValidBean(createEntryFromInput());
            }
        };
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
            switch(fieldName) {
                case "duration" -> markField(durationTextField, errorMessage);
                case "groupTitle" -> markField(groupTitleTextField, errorMessage);
                case "tvgId" -> markField(tvgIdTextField, errorMessage);
                case "tvgName" -> markField(tvgNameTextField, errorMessage);
                case "tvgLogo" -> markField(tvgLogoTextField, errorMessage);
                case "tvgShift" -> markField(tvgShiftTextField, errorMessage);
                case "radio" -> markField(radioTextField, errorMessage);
                case "channelUri" -> markField(channelUriTextField, errorMessage);
                case "name" -> markField(nameTextField, errorMessage);
            }
            // ToDo color VlcOpts in case of validation errors
        }
    }

    private void markField(TextField textField, String errorMessage) {
        textField.setStyle(CSS_INVALID_TEXTFIELD_INPUT);
        textField.setTooltip(new Tooltip(errorMessage));
    }

    private void resetTextFields() {
        durationTextField.setStyle(null);
        durationTextField.setTooltip(null);
        groupTitleTextField.setStyle(null);
        groupTitleTextField.setTooltip(null);
        tvgIdTextField.setStyle(null);
        tvgIdTextField.setTooltip(null);
        tvgNameTextField.setStyle(null);
        tvgNameTextField.setTooltip(null);
        tvgLogoTextField.setStyle(null);
        tvgLogoTextField.setTooltip(null);
        tvgShiftTextField.setStyle(null);
        tvgShiftTextField.setTooltip(null);
        radioTextField.setStyle(null);
        radioTextField.setTooltip(null);
        channelUriTextField.setStyle(null);
        channelUriTextField.setTooltip(null);
        nameTextField.setStyle(null);
        nameTextField.setTooltip(null);
    }

    public void setEntry(Entry entry) {
        durationTextField.setText(entry.getDuration());
        nameTextField.setText(entry.getName());
        channelUriTextField.setText(entry.getChannelUri());
    }
}
package org.bitpunk.m3uedit.controller;

import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;

import java.io.IOException;

abstract public class AbstractDialogController<T> extends Dialog<T> implements Initializable {
    public static final String CSS_INVALID_TEXT_FIELD_INPUT = "-fx-text-box-border: red; -fx-focus-color: red; -fx-control-inner-background: #ffc0c0; -fx-accent: red;";

    public AbstractDialogController(String fxmlFileName, String dialogTitle) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getClassLoader().getResource(fxmlFileName));
            loader.setController(this);

            DialogPane dialogPane = loader.load();

            initModality(Modality.APPLICATION_MODAL);

            setResizable(true);
            setTitle(dialogTitle);
            setDialogPane(dialogPane);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void markFieldInvalid(TextField textField, String errorMessage) {
        textField.setStyle(CSS_INVALID_TEXT_FIELD_INPUT);
        textField.setTooltip(new Tooltip(errorMessage));
    }

    protected void resetTextField(TextField textField) {
        textField.setStyle(null);
        textField.setTooltip(null);
    }

}
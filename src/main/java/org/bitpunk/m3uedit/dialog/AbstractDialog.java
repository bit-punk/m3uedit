package org.bitpunk.m3uedit.dialog;

import javafx.fxml.Initializable;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

abstract public class AbstractDialog<T> extends Dialog<T> implements Initializable {
    public static final String CSS_INVALID_TEXT_FIELD_INPUT = "-fx-text-box-border: red; -fx-focus-color: red";

    protected void markFieldInvalid(TextField textField, String errorMessage) {
        textField.setStyle(CSS_INVALID_TEXT_FIELD_INPUT);
        textField.setTooltip(new Tooltip(errorMessage));
    }

    protected void resetTextField(TextField textField) {
        textField.setStyle(null);
        textField.setTooltip(null);
    }

}
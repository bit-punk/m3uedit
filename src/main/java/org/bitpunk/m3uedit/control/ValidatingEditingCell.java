package org.bitpunk.m3uedit.control;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class ValidatingEditingCell<S> extends TableCell<S, String> {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final Logger log = LoggerFactory.getLogger(ValidatingEditingCell.class);

    private final TextField textField;
    private final BooleanProperty booleanProperty = new SimpleBooleanProperty();

    public ValidatingEditingCell(Predicate<String> validator) {
        this.textField = new TextField();
        booleanProperty.bind(Bindings.createBooleanBinding(() ->
                isNotEmpty(textField.getText()) && validator.test(textField.getText()),
                textField.textProperty()));
        booleanProperty.addListener((obs, wasValid, isValid) -> pseudoClassStateChanged(INVALID, !isValid));
        pseudoClassStateChanged(INVALID, !booleanProperty.get());

        textField.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && booleanProperty.get()) {
                commitEdit(textField.getText());
                getScene().getRoot().requestFocus();
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
            }
        });

        setGraphic(textField);
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        log.debug("updateItem({}, {})", item, empty);
        super.updateItem(item, empty);
        setText(empty ? null : item);
        textField.setText(empty ? null : item);
        setContentDisplay(isEditing() ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.TEXT_ONLY);
    }

    @Override
    public void cancelEdit() {
        log.debug("cancelEdit()");
        super.cancelEdit();
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    @Override
    public void commitEdit(String newValue) {
        log.debug("commitEdit({})", newValue);
        super.commitEdit(newValue);
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    @Override
    public void startEdit() {
        log.debug("startEdit()");
        super.startEdit();
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        textField.selectAll();
        textField.requestFocus();
    }
}
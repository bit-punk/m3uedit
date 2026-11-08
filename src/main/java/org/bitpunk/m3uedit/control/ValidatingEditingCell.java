package org.bitpunk.m3uedit.control;

import lombok.extern.slf4j.Slf4j;
import javafx.beans.binding.StringBinding;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Predicate;

@Slf4j
public class ValidatingEditingCell<T> extends TableCell<T, String> {

    public static final String CSS_INVALID_TEXT_FIELD_INPUT = "-fx-text-box-border: red; -fx-focus-color: red; -fx-control-inner-background: #ffc0c0; -fx-accent: red;";
    private final TextField textField = new TextField();
    private StringBinding isInputValid;


    public ValidatingEditingCell(Predicate<String> validator) {
        initializeBinding(validator);
        initTextField();
        textField.styleProperty().bind(isInputValid);

        setGraphic(textField);
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    private void initTextField() {
        textField.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && isInputValid.get().isEmpty()) {
                commitEdit(textField.getText());
                e.consume();
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
            }
        });
    }

    private void initializeBinding(Predicate<String> validator) {
        isInputValid = new StringBinding() {
            {
                bind(textField.textProperty());
            }

            @Override
            protected String computeValue() {
                return validator.test(textField.getText()) ? "" : CSS_INVALID_TEXT_FIELD_INPUT;
            }
        };
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        log.debug("updateItem({}, {})", item, empty);
        setText(empty ? null : item);
        textField.setText(empty ? null : item);
        setContentDisplay(isEditing() ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.TEXT_ONLY);
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        log.debug("cancelEdit()");
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    @Override
    public void commitEdit(String newValue) {
        super.commitEdit(newValue);
        log.debug("commitEdit({})", newValue);
        setContentDisplay(ContentDisplay.TEXT_ONLY);
    }

    @Override
    public void startEdit() {
        super.startEdit();
        log.debug("startEdit()");
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        textField.selectAll();
        textField.requestFocus();
    }

}
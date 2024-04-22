package org.bitpunk.m3uedit.controller;

import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.layout.VBox;

import java.io.IOException;

public abstract class AbstractController extends VBox implements Initializable {
    public static final String CSS_INVALID_TEXT_FIELD_INPUT = "-fx-text-box-border: red; -fx-focus-color: red; -fx-control-inner-background: #ffc0c0; -fx-accent: red;";

    protected AbstractController(String fxmlFileName) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource(fxmlFileName));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

    }
}

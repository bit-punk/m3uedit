package org.bitpunk.m3uedit.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.util.Themes;

import java.io.IOException;

public abstract class AbstractController extends VBox {
    public static final String CSS_INVALID_TEXT_FIELD_INPUT = Themes.INVALID_INPUT_STYLE;

    protected AbstractController(String fxmlFileName) {
        FXMLLoader fxmlLoader = Messages.fxmlLoader(getClass().getClassLoader().getResource(fxmlFileName));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new FXMLLoadException(fxmlFileName, exception);
        }

    }
}

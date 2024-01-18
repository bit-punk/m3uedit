package org.bitpunk.m3uedit.controller;

import java.net.URL;
import java.util.ResourceBundle;

public class AboutDialogController<T> extends AbstractDialogController<T> {

    public static final String FXML_ABOUT_DIALOG = "fxml/AboutDialog.fxml";

    public AboutDialogController() {
        super(FXML_ABOUT_DIALOG, "About m3uedit");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}
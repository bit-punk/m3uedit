package org.bitpunk.m3uedit.controller;

import javafx.fxml.FXML;
import javafx.scene.text.Text;
import org.bitpunk.m3uedit.util.AppVersion;

import java.net.URL;
import java.util.ResourceBundle;

public class AboutDialogController<T> extends AbstractDialogController<T> {

    public static final String FXML_ABOUT_DIALOG = "fxml/AboutDialog.fxml";

    public AboutDialogController() {
        super(FXML_ABOUT_DIALOG, "About m3uedit");
    }

    @FXML
    private Text versionText;
    @FXML
    private Text buildTimeText;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        versionText.setText("version: " + AppVersion.version());
        buildTimeText.setText("build-time: " + AppVersion.buildTime());
    }

    Text versionText() {
        return versionText;
    }
}
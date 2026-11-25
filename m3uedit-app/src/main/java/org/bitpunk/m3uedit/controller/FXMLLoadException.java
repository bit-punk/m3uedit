package org.bitpunk.m3uedit.controller;

import java.io.IOException;

public class FXMLLoadException extends RuntimeException {
    public FXMLLoadException(String fxmlFileName, IOException cause) {
        super("Failed to load FXML file: " + fxmlFileName, cause);
    }
}

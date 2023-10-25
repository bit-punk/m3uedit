package org.bitpunk.m3uedit.plugins.input;

import org.bitpunk.m3uedit.plugins.Plugin;

import java.io.FileNotFoundException;
import java.io.InputStream;

public interface Input extends Plugin {
    InputStream read() throws FileNotFoundException;
    String getSource();
    void setSource(String source);
}
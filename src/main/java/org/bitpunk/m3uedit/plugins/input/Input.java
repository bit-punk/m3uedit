package org.bitpunk.m3uedit.plugins.input;

import java.io.FileNotFoundException;
import java.io.InputStream;

public interface Input {

    InputStream read() throws FileNotFoundException;
    String getName();
    String getDescription();
}
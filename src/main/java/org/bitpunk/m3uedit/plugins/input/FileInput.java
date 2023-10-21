package org.bitpunk.m3uedit.plugins.input;

import lombok.Data;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@Data
public class FileInput implements Input {

    private final String name = "File Input";
    private final String description = "Reads a playlist from a local file.";
    private String source = "";

    @Override
    public InputStream read() throws FileNotFoundException {
        return new DataInputStream(new FileInputStream(source));
    }

}

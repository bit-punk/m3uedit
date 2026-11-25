package org.bitpunk.m3uedit.parser;

public class ParsingException extends RuntimeException {

    public ParsingException(int line, String message) {
        super(message + " at line " + line);
    }

    public ParsingException(int line, String message, Exception cause) {
        super(message + " at line " + line, cause);
    }

}
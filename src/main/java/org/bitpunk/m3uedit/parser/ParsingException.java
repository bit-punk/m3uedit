package org.bitpunk.m3uedit.parser;

public class ParsingException extends RuntimeException {

    private final int line;

    public ParsingException(int line, String message) {
        super(message + " at line " + line);
        this.line = line;
    }

    public ParsingException(int line, String message, Exception cause) {
        super(message + " at line " + line, cause);
        this.line = line;
    }

    public int getLine() {
        return line;
    }
}
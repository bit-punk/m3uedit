package org.bitpunk.m3uedit.plugins.input;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.PlayList;
import org.bitpunk.m3uedit.parser.Parser;

import java.io.InputStream;

abstract public class AbstractInput {
    protected PlayList<Entry> readData(InputStream inputStream) {
        Parser parser = new Parser();
        return parser.parse(inputStream);
    }
}

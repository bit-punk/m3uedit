package org.bitpunk.m3uedit.plugins.input;

import org.bitpunk.m3uedit.model.EntryList;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;

import java.io.InputStream;

public abstract class AbstractInput extends AbstractPlugin {
    protected EntryList readData(InputStream inputStream) {
        Parser parser = new Parser();
        return parser.parse(inputStream);
    }
}

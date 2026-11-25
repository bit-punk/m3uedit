package org.bitpunk.m3uedit.plugins.filter;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.parser.Parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Objects;

public abstract class AbstractFilterTest {
    protected List<Entry> readList(String resourceName) throws FileNotFoundException {
        List<Entry> entryList;
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource(resourceName)).getFile());
        Parser parser = new Parser();
        entryList = parser.parse(new FileInputStream(file));

        return entryList;
    }
}

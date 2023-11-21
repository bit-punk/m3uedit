package org.bitpunk.m3uedit.model;

import lombok.Data;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;

import java.util.ArrayList;

@Data
public class Project {
    private String name;
    private ArrayList<Input> inputs;
    private ArrayList<Filter> filters;
    private ArrayList<Sorter> sorters;
    private ArrayList<Entry> entries;
    private ArrayList<Output> outputs;

}

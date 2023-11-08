package org.bitpunk.m3uedit.model;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.Data;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;

@Data
public class DataModel {
    private static final Logger log = LoggerFactory.getLogger(DataModel.class);

    private ObservableList<Input> inputs = FXCollections.observableArrayList();
    private ObservableList<Filter> filters = FXCollections.observableArrayList();
    private ObservableList<Sorter> sorters = FXCollections.observableArrayList();
    private ObservableList<Entry> entries = FXCollections.observableArrayList();
    private ObservableList<Output> outputs = FXCollections.observableArrayList();

    public DataModel() {
        inputs.addListener((ListChangeListener<? super Input>) listChangeListener -> loadEntries());
    }

    public void loadEntries() {
        for (Input currentInput : inputs) {
            log.debug("reading from: {}", currentInput);
            try {
                entries.addAll(currentInput.read());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public void saveEntries() {
        for (Output output : outputs) {
            output.write(entries);
        }
    }
}
package org.bitpunk.m3uedit.model;

import jakarta.validation.constraints.NotNull;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.Data;
import org.bitpunk.m3uedit.NamedItem;
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
    @NotNull
    private String name;

    private boolean changed = true;

    private ObservableList<Input> inputs = FXCollections.observableArrayList();
    private ObservableList<Filter> filters = FXCollections.observableArrayList();
    private ObservableList<Sorter> sorters = FXCollections.observableArrayList();
    private ObservableList<Entry> entries = FXCollections.observableArrayList();
    private ObservableList<Output> outputs = FXCollections.observableArrayList();


    /**
     * Using XStream for de-serialization will not invoke the constructor, however it will invoke this method.
     * This method is used during the deserialization process to resolve the read object.
     * It initializes the changed data bindings and returns the deserialized object.
     *
     * @return The deserialized object with initialized changed data bindings.
     */
    private Object readResolve() {
        initChangedDataBindings();
        return this;
    }

    public DataModel(String name) {
        this.name = name;
        // add a new Listener that will automatically load the entries from the Input that was added to inputs
        inputs.addListener((ListChangeListener<? super Input>) listChangeListener -> loadEntries());
        // add Change-Listeners to all observable fields to set the 'dirty'-flag for the DataModel in case of changes
        initChangedDataBindings();
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
        outputs.forEach(output -> {
            log.debug("saving {} to output {}", entries.size(), output);
            output.write(entries);
        });
    }

    /**
     * Initializes the changed data bindings for this object.
     * Whenever any of the input lists changes, the 'changed' flag is set to true.
     * This method adds change listeners to the input lists to provide a dirty-flag indicating changes since last save.
     *
     * @implNote This method is intended to be called during the initialization process of the object.
     */
    private void initChangedDataBindings() {
        inputs.addListener(this::onChanged);
        filters.addListener(this::onChanged);
        sorters.addListener(this::onChanged);
        entries.addListener(this::onChanged);
        outputs.addListener(this::onChanged);
    }

    private void onChanged(ListChangeListener.Change<? extends NamedItem> c) {
        changed = true;
    }
}
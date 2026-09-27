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
import java.util.ArrayList;
import java.util.List;

@Data
public class DataModel {
    private static final Logger log = LoggerFactory.getLogger(DataModel.class);
    @NotNull
    private String name;

    private boolean changed = true;

    private ObservableList<Input> inputs = FXCollections.observableArrayList();
    // initialize Observable Lists
    private ObservableList<Filter> filters = FXCollections.observableArrayList();
    private ObservableList<Sorter> sorters = FXCollections.observableArrayList();
    private ObservableList<Entry> rawEntryList = FXCollections.observableArrayList();
    private ObservableList<Entry> filteredEntryList = FXCollections.observableArrayList();
    private ObservableList<Entry> sortedEntryList = FXCollections.observableArrayList();
    private ObservableList<Output> outputs = FXCollections.observableArrayList();

    public DataModel(String name) {
        this.name = name;
        // add Change-Listeners to all observable fields to set the 'dirty'-flag for the DataModel in case of changes
        initDirtyFlagBindings();
        initChangedInputsListener();    // reload entries to rawEntries with each change in the list of Inputs
        initChangedRawEntries();
        initChangedFilteredEntries();
    }

    /**
     * Using XStream for deserialization will not invoke the constructor, however, it will invoke this method.
     * This method is used during the deserialization process to resolve the read object.
     * It initializes the changed data bindings and returns the deserialized object.
     *
     * @return The deserialized object with initialized changed data bindings.
     */
    @SuppressWarnings("unused")
    private Object readResolve() {
        initDirtyFlagBindings();
        initChangedInputsListener();
        initChangedRawEntries();
        initChangedFilteredEntries();
        return this;
    }

    public List<Entry> loadEntries(List<Input> inputs) {
        List<Entry> loadedEntries = new ArrayList<>();
        for (Input currentInput : inputs) {
            log.debug("reading from: {}", currentInput);
            try {
                loadedEntries.addAll(currentInput.read());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return loadedEntries;
    }

    public void saveEntries() {
        outputs.forEach(output -> {
            log.debug("saving {} to output {}", sortedEntryList.size(), output);
            output.write(sortedEntryList);
        });
    }

    /**
     * Initializes the changed data bindings for this object.
     * Whenever any of the input lists changes, the 'changed' flag is set to true.
     * This method adds change listeners to the input lists to provide a dirty-flag indicating changes since last save.
     *
     * @implNote This method is intended to be called during the initialization process of the object.
     */
    private void initDirtyFlagBindings() {
        inputs.addListener(this::onChanged);
        filters.addListener(this::onChanged);
        sorters.addListener(this::onChanged);
        rawEntryList.addListener(this::onChanged);
        filteredEntryList.addListener(this::onChanged);
        sortedEntryList.addListener(this::onChanged);
        outputs.addListener(this::onChanged);
    }

    private void onChanged(ListChangeListener.Change<? extends NamedItem> c) {
        changed = true;
    }

    /**
     * Initializes the listener for changes in the input list.
     * When the input list changes, this method clears the rawEntryList and loads the entries from the input list.
     *
     * @implNote This method is intended to be called during the initialization process of the DataModel object.
     */
    private void initChangedInputsListener() {
        inputs.addListener((ListChangeListener<? super Input>) listChangeListener -> {
            log.debug("changed Inputs: {}", inputs);
            rawEntryList.clear();
            List<Entry> loadedEntries = loadEntries(this.inputs);
            log.debug("raw Entries loaded: {}", loadedEntries);
            rawEntryList.addAll(loadedEntries);
        });
    }

    private void initChangedRawEntries() {
        // apply enabled Filters and save the resulting Entry-List in filteredEntryList
        rawEntryList.addListener((ListChangeListener<? super Entry>) listChangeListener -> filterRawEntries());
    }

    public void filterRawEntries(){
        filteredEntryList.clear();
        filteredEntryList.addAll(FXCollections.observableArrayList(filterEntries(filters, rawEntryList)));
    }

    private List<Entry> filterEntries(List<Filter> filters, List<Entry> entries) {
        List<Entry> filteredEntries = new ArrayList<>(entries);
        for (Filter filter : filters) {
            if(filter.getEnabled().get()) {
                filteredEntries = filter.filter(filteredEntries);
            }
        }
        return filteredEntries;
    }

    private void initChangedFilteredEntries() {
        // apply enabled Sorters and save the resulting Entry-List in sortedEntryList
        filteredEntryList.addListener((ListChangeListener<? super Entry>) listChangeListener -> sortFilteredEntries());
    }

    public void sortFilteredEntries() {
        sortedEntryList.clear();
        sortedEntryList.addAll(FXCollections.observableArrayList(sortEntries(sorters, filteredEntryList)));
    }

    private List<Entry> sortEntries(List<Sorter> sorters, List<Entry> entries) {
        List<Entry> sortedEntries = new ArrayList<>(entries);
        for (Sorter sorter : sorters) {
            if(sorter.getEnabled().get()) {
                sortedEntries = sorter.sort(sortedEntries);
            }
        }
        return sortedEntries;
    }

}
package org.bitpunk.m3uedit.model;

import jakarta.validation.constraints.NotNull;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The model of a project: its inputs, filters, sorters and outputs, plus the entry-lists computed from them.
 * <p>
 * The entry-lists are derived data and therefore not persisted. They are (re-)computed in the background
 * whenever inputs, filters or sorters change: inputs &rarr; {@link #rawEntryList} &rarr; enabled filters &rarr;
 * {@link #filteredEntryList} &rarr; enabled sorters &rarr; {@link #sortedEntryList}.
 * The results are applied to the observable lists on the JavaFX application thread.
 * <p>
 * The methods triggering a re-computation must be called on the JavaFX application thread.
 */
@Data
public class DataModel {
    private static final Logger log = LoggerFactory.getLogger(DataModel.class);
    private static final ExecutorService DEFAULT_BACKGROUND_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "datamodel-worker");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * The step a re-computation starts with; all following steps are computed as well.
     */
    enum Step {
        LOAD_INPUTS, FILTER, SORT
    }

    private record Result(List<Entry> raw, List<Entry> filtered, List<Entry> sorted, List<String> inputErrors) {
    }

    @NotNull
    private String name;

    private boolean changed = true;

    // initialize Observable Lists; the setters are implemented explicitly to move the listeners to a new list
    @Setter(AccessLevel.NONE)
    private ObservableList<Input> inputs = FXCollections.observableArrayList();
    @Setter(AccessLevel.NONE)
    private ObservableList<Filter> filters = FXCollections.observableArrayList();
    @Setter(AccessLevel.NONE)
    private ObservableList<Sorter> sorters = FXCollections.observableArrayList();
    @Setter(AccessLevel.NONE)
    private ObservableList<Output> outputs = FXCollections.observableArrayList();

    // derived data: transient fields are neither persisted by XStream nor part of equals/hashCode
    @ToString.Exclude
    private transient ObservableList<Entry> rawEntryList;
    @ToString.Exclude
    private transient ObservableList<Entry> filteredEntryList;
    @ToString.Exclude
    private transient ObservableList<Entry> sortedEntryList;

    /**
     * The file this project has been loaded from or saved to, null for a new project.
     */
    @ToString.Exclude
    private transient Path projectFile;

    /**
     * Executes the (potentially long-running) re-computation of the entry-lists.
     */
    @ToString.Exclude
    private transient Executor backgroundExecutor;
    /**
     * Executes the application of computed results to the observable lists (the JavaFX application thread).
     */
    @ToString.Exclude
    private transient Executor fxExecutor;
    /**
     * Receives error messages to be shown to the user, e.g. if an input cannot be read.
     */
    @ToString.Exclude
    private transient Consumer<String> errorHandler;

    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ReadOnlyBooleanWrapper busy;
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient long generation;
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient Step pendingStep;
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient boolean applyingResults;

    // listeners are kept, so they can be moved to a new list passed to a setter
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ListChangeListener<Plugin> pluginListListener;
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ChangeListener<Boolean> pluginEnabledListener;
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ListChangeListener<Input> inputsRecomputeListener;

    public DataModel(String name) {
        this.name = name;
        initTransientState();
    }

    /**
     * Using XStream for deserialization will not invoke the constructor (nor field initializers),
     * however, it will invoke this method to resolve the read object.
     * It initializes the derived entry-lists and the listeners and returns the deserialized object.
     *
     * @return The deserialized object with initialized listeners.
     */
    @SuppressWarnings("unused")
    private Object readResolve() {
        initTransientState();
        return this;
    }

    private void initTransientState() {
        // lists missing in a project-file are null after deserialization
        inputs = inputs != null ? inputs : FXCollections.observableArrayList();
        filters = filters != null ? filters : FXCollections.observableArrayList();
        sorters = sorters != null ? sorters : FXCollections.observableArrayList();
        outputs = outputs != null ? outputs : FXCollections.observableArrayList();
        rawEntryList = FXCollections.observableArrayList();
        filteredEntryList = FXCollections.observableArrayList();
        sortedEntryList = FXCollections.observableArrayList();
        busy = new ReadOnlyBooleanWrapper(this, "busy", false);
        // the 'dirty'-flag is set with each change of a persisted list, or the enabled-state of one of its plugins
        pluginEnabledListener = (observable, oldValue, newValue) -> changed = true;
        pluginListListener = this::onPluginListChanged;
        inputsRecomputeListener = change -> reloadInputs();
        observePluginList(inputs);
        observePluginList(filters);
        observePluginList(sorters);
        observePluginList(outputs);
        initRecomputeListeners();
    }

    private void observePluginList(ObservableList<? extends Plugin> plugins) {
        plugins.forEach(plugin -> plugin.getEnabled().addListener(pluginEnabledListener));
        plugins.addListener(pluginListListener);
    }

    private void stopObservingPluginList(ObservableList<? extends Plugin> plugins) {
        plugins.forEach(plugin -> plugin.getEnabled().removeListener(pluginEnabledListener));
        plugins.removeListener(pluginListListener);
    }

    private void onPluginListChanged(ListChangeListener.Change<? extends Plugin> change) {
        changed = true;
        while (change.next()) {
            change.getRemoved().forEach(plugin -> plugin.getEnabled().removeListener(pluginEnabledListener));
            change.getAddedSubList().forEach(plugin -> plugin.getEnabled().addListener(pluginEnabledListener));
        }
    }

    /**
     * Replaces the list of inputs. The entries are not reloaded, call {@link #reloadInputs()} to do so.
     */
    public void setInputs(ObservableList<Input> inputs) {
        stopObservingPluginList(this.inputs);
        this.inputs.removeListener(inputsRecomputeListener);
        this.inputs = inputs;
        observePluginList(this.inputs);
        this.inputs.addListener(inputsRecomputeListener);
    }

    public void setFilters(ObservableList<Filter> filters) {
        stopObservingPluginList(this.filters);
        this.filters = filters;
        observePluginList(this.filters);
    }

    public void setSorters(ObservableList<Sorter> sorters) {
        stopObservingPluginList(this.sorters);
        this.sorters = sorters;
        observePluginList(this.sorters);
    }

    public void setOutputs(ObservableList<Output> outputs) {
        stopObservingPluginList(this.outputs);
        this.outputs = outputs;
        observePluginList(this.outputs);
    }

    private void initRecomputeListeners() {
        // reload entries with each change in the list of Inputs
        inputs.addListener(inputsRecomputeListener);
        // entries removed by the user from the raw / filtered list are propagated to the following lists
        rawEntryList.addListener((ListChangeListener<? super Entry>) change -> {
            if (!applyingResults) {
                filterRawEntries();
            }
        });
        filteredEntryList.addListener((ListChangeListener<? super Entry>) change -> {
            if (!applyingResults) {
                sortFilteredEntries();
            }
        });
    }

    /**
     * @return true while the entry-lists are being re-computed in the background
     */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    public boolean isBusy() {
        return busy.get();
    }

    /**
     * Reads all inputs in the background and re-computes all entry-lists.
     */
    public void reloadInputs() {
        recompute(Step.LOAD_INPUTS);
    }

    /**
     * Applies the enabled filters to the raw entries in the background and re-computes the following entry-lists.
     */
    public void filterRawEntries() {
        recompute(Step.FILTER);
    }

    /**
     * Applies the enabled sorters to the filtered entries in the background.
     */
    public void sortFilteredEntries() {
        recompute(Step.SORT);
    }

    private void recompute(Step requestedStep) {
        // a computation still running might have been started with an earlier step: it gets superseded,
        // so the new computation has to start with the earliest step requested
        Step step = pendingStep != null && pendingStep.compareTo(requestedStep) < 0 ? pendingStep : requestedStep;
        pendingStep = step;
        long currentGeneration = ++generation;
        busy.set(true);

        // take snapshots on the calling (JavaFX) thread, the background thread must not access the observable lists
        List<Input> inputsSnapshot = new ArrayList<>(inputs);
        List<Filter> enabledFilters = filters.stream().filter(filter -> filter.getEnabled().get()).toList();
        List<Sorter> enabledSorters = sorters.stream().filter(sorter -> sorter.getEnabled().get()).toList();
        List<Entry> rawSnapshot = new ArrayList<>(rawEntryList);
        List<Entry> filteredSnapshot = new ArrayList<>(filteredEntryList);
        log.debug("re-computing entries of project '{}' starting with step {}", name, step);

        getBackgroundExecutorOrDefault().execute(() -> {
            try {
                List<String> inputErrors = new ArrayList<>();
                List<Entry> raw = step == Step.LOAD_INPUTS ? loadEntries(inputsSnapshot, inputErrors) : rawSnapshot;
                List<Entry> filtered = step.compareTo(Step.SORT) < 0 ? filterEntries(enabledFilters, raw) : filteredSnapshot;
                List<Entry> sorted = sortEntries(enabledSorters, filtered);
                Result result = new Result(raw, filtered, sorted, inputErrors);
                getFxExecutorOrDefault().execute(() -> applyResult(currentGeneration, step, result));
            } catch (RuntimeException e) {
                log.error("Error re-computing the entries of project '{}'", name, e);
                getFxExecutorOrDefault().execute(() -> applyFailure(currentGeneration, e));
            }
        });
    }

    private void applyResult(long resultGeneration, Step step, Result result) {
        if (resultGeneration != generation) {
            log.debug("discarding outdated result of project '{}'", name);
            return;
        }
        applyingResults = true;
        try {
            if (step == Step.LOAD_INPUTS) {
                rawEntryList.setAll(result.raw());
            }
            if (step.compareTo(Step.SORT) < 0) {
                filteredEntryList.setAll(result.filtered());
            }
            sortedEntryList.setAll(result.sorted());
        } finally {
            applyingResults = false;
        }
        pendingStep = null;
        busy.set(false);
        if (!result.inputErrors().isEmpty()) {
            reportError("Some inputs could not be read:\n" + String.join("\n", result.inputErrors()));
        }
    }

    private void applyFailure(long resultGeneration, RuntimeException e) {
        if (resultGeneration != generation) {
            return;
        }
        pendingStep = null;
        busy.set(false);
        reportError("Error computing the playlist: " + e.getMessage());
    }

    private void reportError(String message) {
        if (errorHandler != null) {
            errorHandler.accept(message);
        } else {
            log.warn(message);
        }
    }

    /**
     * Reads all given inputs. Inputs that cannot be read are skipped, their errors are added to {@code errors}.
     */
    List<Entry> loadEntries(List<Input> inputs, List<String> errors) {
        List<Entry> loadedEntries = new ArrayList<>();
        for (Input currentInput : inputs) {
            log.debug("reading from: {}", currentInput.getName());
            try {
                loadedEntries.addAll(currentInput.read());
            } catch (Exception e) {
                log.error("Error reading input {} ({})", currentInput.getName(), currentInput.getSource(), e);
                errors.add("%s (%s): %s".formatted(currentInput.getName(), currentInput.getSource(), e.getMessage()));
            }
        }
        log.debug("{} raw entries loaded", loadedEntries.size());
        return loadedEntries;
    }

    public void saveEntries() {
        outputs.forEach(output -> {
            log.debug("saving {} to output {}", sortedEntryList.size(), output);
            output.write(sortedEntryList);
        });
    }

    private static List<Entry> filterEntries(List<Filter> filters, List<Entry> entries) {
        List<Entry> filteredEntries = new ArrayList<>(entries);
        for (Filter filter : filters) {
            filteredEntries = filter.filter(filteredEntries);
        }
        return filteredEntries;
    }

    private static List<Entry> sortEntries(List<Sorter> sorters, List<Entry> entries) {
        List<Entry> sortedEntries = new ArrayList<>(entries);
        for (Sorter sorter : sorters) {
            sortedEntries = sorter.sort(sortedEntries);
        }
        return sortedEntries;
    }

    private Executor getBackgroundExecutorOrDefault() {
        return backgroundExecutor != null ? backgroundExecutor : DEFAULT_BACKGROUND_EXECUTOR;
    }

    private Executor getFxExecutorOrDefault() {
        return fxExecutor != null ? fxExecutor : Platform::runLater;
    }
}

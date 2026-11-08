package org.bitpunk.m3uedit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.util.EntryMapper;
import org.bitpunk.m3uedit.util.UncaughtExceptionHandler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
@Slf4j
@Getter
@Setter
@JsonPropertyOrder({"name", "inputs", "filters", "sorters", "outputs", "manualChanges"})
public class DataModel {
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

    /**
     * @param pipeline       the intermediate results of the steps
     * @param playlist       the sorted entries with the manual changes applied
     * @param appliedChanges the number of manual changes applied to the playlist
     */
    private record Result(PipelineResult pipeline, List<Entry> playlist, int appliedChanges, List<String> inputErrors) {
    }

    @NotNull
    private String name;

    /**
     * The 'dirty'-flag: the state of the editor, not of the project.
     */
    @JsonIgnore
    private boolean changed = true;

    // the setters are implemented explicitly to move the listeners to a new list
    @Setter(AccessLevel.NONE)
    private ObservableList<Input> inputs;
    @Setter(AccessLevel.NONE)
    private ObservableList<Filter> filters;
    @Setter(AccessLevel.NONE)
    private ObservableList<Sorter> sorters;
    @Setter(AccessLevel.NONE)
    private ObservableList<Output> outputs;

    /**
     * The changes the user made to the playlist (the sorted entries), in the order they have been made.
     * They are re-applied whenever the playlist is re-computed.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ObservableList<ManualChange> manualChanges;

    // derived data: transient fields are not persisted
    private transient ObservableList<Entry> rawEntryList;
    private transient ObservableList<Entry> filteredEntryList;
    private transient ObservableList<Entry> sortedEntryList;

    /**
     * The file this project has been loaded from or saved to, null for a new project.
     */
    private transient Path projectFile;

    /**
     * Executes the (potentially long-running) re-computation of the entry-lists.
     */
    private transient Executor backgroundExecutor;
    /**
     * Executes the application of computed results to the observable lists (the JavaFX application thread).
     */
    private transient Executor fxExecutor;
    /**
     * Receives error messages to be shown to the user, e.g. if an input cannot be read.
     */
    private transient Consumer<String> errorHandler;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ReadOnlyBooleanWrapper busy;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ReadOnlyBooleanWrapper saving;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ReadOnlyObjectWrapper<PipelineResult> pipelineResult;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient long generation;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient Step pendingStep;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient boolean applyingResults;

    // listeners are kept, so they can be moved to a new list passed to a setter
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ListChangeListener<Plugin> pluginListListener;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ChangeListener<Boolean> pluginEnabledListener;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ListChangeListener<Input> inputsRecomputeListener;

    public DataModel(String name) {
        this(name, null, null, null, null, null);
    }

    /**
     * Creates a project loaded from a project-file. Lists missing in the project-file are created empty.
     */
    @JsonCreator
    DataModel(@JsonProperty("name") String name,
              @JsonProperty("inputs") List<Input> inputs,
              @JsonProperty("filters") List<Filter> filters,
              @JsonProperty("sorters") List<Sorter> sorters,
              @JsonProperty("outputs") List<Output> outputs,
              @JsonProperty("manualChanges") List<ManualChange> manualChanges) {
        this.name = name;
        this.inputs = observableListOf(inputs);
        this.filters = observableListOf(filters);
        this.sorters = observableListOf(sorters);
        this.outputs = observableListOf(outputs);
        this.manualChanges = observableListOf(manualChanges);
        initTransientState();
    }

    private static <T> ObservableList<T> observableListOf(List<T> list) {
        if (list == null) {
            return FXCollections.observableArrayList();
        }
        return FXCollections.observableArrayList(list.stream().filter(Objects::nonNull).toList());
    }

    private void initTransientState() {
        rawEntryList = FXCollections.observableArrayList();
        filteredEntryList = FXCollections.observableArrayList();
        sortedEntryList = FXCollections.observableArrayList();
        busy = new ReadOnlyBooleanWrapper(this, "busy", false);
        saving = new ReadOnlyBooleanWrapper(this, "saving", false);
        pipelineResult = new ReadOnlyObjectWrapper<>(this, "pipelineResult", PipelineResult.EMPTY);
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
     * @return the intermediate results of the last computation, updated together with the entry-lists
     */
    public ReadOnlyObjectProperty<PipelineResult> pipelineResultProperty() {
        return pipelineResult.getReadOnlyProperty();
    }

    public PipelineResult getPipelineResult() {
        return pipelineResult.get();
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
        // the results of the steps not re-computed are taken over from the previous computation (immutable)
        PipelineResult previous = pipelineResult.get();
        List<ManualChange> changesSnapshot = List.copyOf(manualChanges);
        log.debug("re-computing entries of project '{}' starting with step {}", name, step);

        getBackgroundExecutorOrDefault().execute(() -> {
            try {
                List<String> inputErrors = new ArrayList<>();
                Map<Entry, Input> sources = new IdentityHashMap<>();
                Map<Plugin, StepResult> steps = new IdentityHashMap<>();
                Map<Entry, Filter> removedBy = new IdentityHashMap<>();
                List<Entry> raw;
                if (step == Step.LOAD_INPUTS) {
                    raw = loadEntries(inputsSnapshot, inputErrors, sources);
                } else {
                    raw = rawSnapshot;
                    sources.putAll(previous.sources());
                }
                List<Entry> filtered;
                if (step.compareTo(Step.SORT) < 0) {
                    filtered = filterEntries(enabledFilters, raw, steps, removedBy);
                } else {
                    filtered = filteredSnapshot;
                    previous.steps().forEach((plugin, stepResult) -> {
                        if (plugin instanceof Filter) {
                            steps.put(plugin, stepResult);
                        }
                    });
                    removedBy.putAll(previous.removedBy());
                }
                List<Entry> sorted = sortEntries(enabledSorters, filtered, steps);
                List<Entry> playlist = ManualChange.applyAll(sorted, changesSnapshot);
                Result result = new Result(new PipelineResult(raw, filtered, sorted, sources, steps, removedBy),
                        playlist, changesSnapshot.size(), inputErrors);
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
            PipelineResult pipeline = result.pipeline();
            if (step == Step.LOAD_INPUTS) {
                rawEntryList.setAll(pipeline.raw());
            }
            if (step.compareTo(Step.SORT) < 0) {
                filteredEntryList.setAll(pipeline.filtered());
            }
            sortedEntryList.setAll(withChangesMadeMeanwhile(result));
            pipelineResult.set(pipeline);
        } finally {
            applyingResults = false;
        }
        pendingStep = null;
        busy.set(false);
        if (!result.inputErrors().isEmpty()) {
            reportError("Some inputs could not be read:\n" + String.join("\n", result.inputErrors()));
        }
    }

    /**
     * The user may have changed the playlist while it was computed in the background: these changes are applied
     * as well, so they are not lost.
     */
    private List<Entry> withChangesMadeMeanwhile(Result result) {
        if (manualChanges.size() <= result.appliedChanges()) {
            return result.playlist();
        }
        return ManualChange.applyAll(result.playlist(), manualChanges.subList(result.appliedChanges(), manualChanges.size()));
    }

    /**
     * @return the changes the user made to the playlist, in the order they have been made (unmodifiable)
     */
    public ObservableList<ManualChange> getManualChanges() {
        return FXCollections.unmodifiableObservableList(manualChanges);
    }

    /**
     * Removes the given entries from the playlist (the sorted entries).
     * Must be called on the JavaFX application thread.
     */
    public void removeEntries(List<Entry> entries) {
        for (Entry entry : List.copyOf(entries)) {
            int index = indexOfSortedEntry(entry);
            if (index >= 0) {
                sortedEntryList.remove(index);
                recordManualChange(ManualChange.remove(entry));
            }
        }
    }

    /**
     * Adds the entry to the end of the playlist (the sorted entries).
     * Must be called on the JavaFX application thread.
     */
    public void addEntry(Entry entry) {
        sortedEntryList.add(entry);
        recordManualChange(ManualChange.add(entry));
    }

    /**
     * Replaces an entry of the playlist (the sorted entries) by the edited values.
     * Must be called on the JavaFX application thread.
     *
     * @return the entry now contained in the playlist, or the given entry if it is not part of the playlist
     */
    public Entry editEntry(Entry entry, Entry edited) {
        int index = indexOfSortedEntry(entry);
        if (index < 0) {
            return entry;
        }
        Entry updated = new Entry();
        EntryMapper.mapEntry(edited, updated);
        recordManualChange(ManualChange.edit(EntryKey.of(entry), updated));
        sortedEntryList.set(index, updated);
        return updated;
    }

    /**
     * Moves the given entries of the playlist (the sorted entries), keeping their order.
     * Must be called on the JavaFX application thread.
     *
     * @param targetIndex the index of the first entry moved, within the playlist without the moved entries
     */
    public void moveEntries(List<Entry> entries, int targetIndex) {
        List<Entry> moved = List.copyOf(entries).stream().filter(entry -> indexOfSortedEntry(entry) >= 0).toList();
        if (moved.isEmpty()) {
            return;
        }
        moved.forEach(entry -> sortedEntryList.remove(indexOfSortedEntry(entry)));
        int index = Math.clamp(targetIndex, 0, sortedEntryList.size());
        sortedEntryList.addAll(index, moved);
        for (int i = index; i < index + moved.size(); i++) {
            recordManualChange(ManualChange.move(sortedEntryList.get(i), i == 0 ? null : sortedEntryList.get(i - 1)));
        }
    }

    /**
     * Discards all changes the user made to the playlist and re-computes it.
     * Must be called on the JavaFX application thread.
     */
    public void resetManualChanges() {
        if (manualChanges.isEmpty()) {
            return;
        }
        manualChanges.clear();
        changed = true;
        sortFilteredEntries();
    }

    private void recordManualChange(ManualChange change) {
        manualChanges.add(change);
        changed = true;
    }

    /**
     * Entries are compared by identity, there may be several entries with the same values.
     */
    private int indexOfSortedEntry(Entry entry) {
        for (int i = 0; i < sortedEntryList.size(); i++) {
            if (sortedEntryList.get(i) == entry) {
                return i;
            }
        }
        return -1;
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
        return loadEntries(inputs, errors, new IdentityHashMap<>());
    }

    /**
     * Reads all given inputs, remembering the input each entry has been read from in {@code sources}.
     */
    private List<Entry> loadEntries(List<Input> inputs, List<String> errors, Map<Entry, Input> sources) {
        List<Entry> loadedEntries = new ArrayList<>();
        for (Input currentInput : inputs) {
            log.debug("reading from: {}", currentInput.getName());
            try {
                List<Entry> entries = currentInput.read();
                loadedEntries.addAll(entries);
                entries.forEach(entry -> sources.putIfAbsent(entry, currentInput));
            } catch (Exception e) {
                log.error("Error reading input {} ({})", currentInput.getName(), currentInput.getSource(), e);
                errors.add("%s (%s): %s".formatted(currentInput.getName(), currentInput.getSource(),
                        UncaughtExceptionHandler.describe(e)));
            }
        }
        log.debug("{} raw entries loaded", loadedEntries.size());
        return loadedEntries;
    }

    /**
     * Writes the sorted entries to all outputs in the background.
     * Outputs failing are reported, the remaining ones are written anyway.
     * Outputs may ask the user (e.g. for a password) from the background thread, the dialogs have to be shown
     * on the JavaFX application thread by the outputs themselves.
     */
    public void saveEntries() {
        if (isSaving()) {
            log.debug("project '{}' is already being saved to its outputs", name);
            return;
        }
        saving.set(true);
        // take snapshots on the calling (JavaFX) thread, the background thread must not access the observable lists
        List<Output> outputsSnapshot = new ArrayList<>(outputs);
        List<Entry> playlist = List.copyOf(sortedEntryList);
        getBackgroundExecutorOrDefault().execute(() -> {
            List<String> errors = new ArrayList<>();
            try {
                writeToOutputs(outputsSnapshot, playlist, errors);
            } finally {
                getFxExecutorOrDefault().execute(() -> {
                    saving.set(false);
                    if (!errors.isEmpty()) {
                        reportError("The playlist could not be written to all outputs:\n" + String.join("\n", errors));
                    }
                });
            }
        });
    }

    private void writeToOutputs(List<Output> outputs, List<Entry> playlist, List<String> errors) {
        for (Output output : outputs) {
            log.debug("saving {} entries to output {}", playlist.size(), output.getName());
            try {
                output.write(playlist);
            } catch (RuntimeException e) {
                log.error("Error writing to output {} ({})", output.getName(), output.getOutput(), e);
                errors.add("%s: %s".formatted(output.getName(), UncaughtExceptionHandler.describe(e)));
            }
        }
    }

    /**
     * @return true while the playlist is written to the outputs in the background
     */
    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    public boolean isSaving() {
        return saving.get();
    }

    private static List<Entry> filterEntries(List<Filter> filters, List<Entry> entries,
                                             Map<Plugin, StepResult> steps, Map<Entry, Filter> removedBy) {
        List<Entry> filteredEntries = new ArrayList<>(entries);
        for (Filter filter : filters) {
            List<Entry> input = filteredEntries;
            filteredEntries = filter.filter(input);
            StepResult stepResult = StepResult.of(input, filteredEntries);
            steps.put(filter, stepResult);
            if (stepResult.removedCount() > 0) {
                Set<Entry> kept = Collections.newSetFromMap(new IdentityHashMap<>());
                kept.addAll(filteredEntries);
                input.stream().filter(entry -> !kept.contains(entry)).forEach(entry -> removedBy.putIfAbsent(entry, filter));
            }
        }
        return filteredEntries;
    }

    private static List<Entry> sortEntries(List<Sorter> sorters, List<Entry> entries, Map<Plugin, StepResult> steps) {
        List<Entry> sortedEntries = new ArrayList<>(entries);
        for (Sorter sorter : sorters) {
            List<Entry> input = sortedEntries;
            sortedEntries = sorter.sort(input);
            steps.put(sorter, StepResult.of(input, sortedEntries));
        }
        return sortedEntries;
    }

    private Executor getBackgroundExecutorOrDefault() {
        return backgroundExecutor != null ? backgroundExecutor : DEFAULT_BACKGROUND_EXECUTOR;
    }

    private Executor getFxExecutorOrDefault() {
        return fxExecutor != null ? fxExecutor : Platform::runLater;
    }

    /**
     * Projects are compared by identity, see {@link org.bitpunk.m3uedit.plugins.AbstractPlugin#toString()}.
     */
    @Override
    public String toString() {
        return "DataModel[name=%s, projectFile=%s, inputs=%d, filters=%d, sorters=%d, outputs=%d]".formatted(
                name, projectFile, inputs.size(), filters.size(), sorters.size(), outputs.size());
    }
}

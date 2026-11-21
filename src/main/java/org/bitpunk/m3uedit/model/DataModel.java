package org.bitpunk.m3uedit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.enricher.Attachment;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.enricher.Enrichment;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputReadException;
import org.bitpunk.m3uedit.plugins.output.Export;
import org.bitpunk.m3uedit.plugins.output.Export.ExportFile;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.util.EntryMapper;
import org.bitpunk.m3uedit.util.UncaughtExceptionHandler;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * whenever inputs, filters, sorters or enrichers change: inputs &rarr; {@link #rawEntryList} &rarr; enabled filters
 * &rarr; {@link #filteredEntryList} &rarr; enabled sorters &rarr; enabled enrichers &rarr; manual changes &rarr;
 * {@link #sortedEntryList} (the playlist).
 * The results are applied to the observable lists on the JavaFX application thread.
 * <p>
 * The methods triggering a re-computation must be called on the JavaFX application thread.
 */
@Slf4j
@Getter
@Setter
@JsonPropertyOrder({"name", "inputs", "filters", "sorters", "enrichers", "outputs", "epgUrls", "manualChanges"})
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
        LOAD_INPUTS, FILTER, SORT, ENRICH
    }

    /**
     * @param pipeline       the intermediate results of the steps
     * @param playlist       the sorted entries with the manual changes applied
     * @param appliedChanges the number of manual changes applied to the playlist
     */
    private record Result(PipelineResult pipeline, List<Entry> playlist, int appliedChanges, Map<Input, String> inputErrors,
                          List<String> inputEpgUrls, List<String> enricherWarnings) {
    }

    @NotNull
    private String name;

    /**
     * The 'dirty'-flag: the state of the editor, not of the project. Observable, e.g. for the tab of the project.
     */
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient BooleanProperty changed = new SimpleBooleanProperty(this, "changed", true);

    // the setters are implemented explicitly to move the listeners to a new list
    @Setter(AccessLevel.NONE)
    private ObservableList<Input> inputs;
    @Setter(AccessLevel.NONE)
    private ObservableList<Filter> filters;
    @Setter(AccessLevel.NONE)
    private ObservableList<Sorter> sorters;
    @Setter(AccessLevel.NONE)
    private ObservableList<Enricher> enrichers;
    @Setter(AccessLevel.NONE)
    private ObservableList<Output> outputs;

    /**
     * The urls of the EPG (XMLTV) files written to the playlist header. If empty, the EPG urls found in the
     * headers of the inputs are written instead, see {@link #getEffectiveEpgUrls()}.
     */
    @Setter(AccessLevel.NONE)
    private ObservableList<String> epgUrls;

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
    /**
     * The errors of the inputs that could not be read when the inputs have been read last.
     * -- GETTER --
     *
     * @return the errors of the inputs that could not be read when the inputs have been read last, empty if all
     * could be read

     */
    private transient List<String> inputErrors = List.of();
    /**
     * A step of the history of the changes made by hand: the changes before and after it.
     */
    private record HistoryStep(List<ManualChange> before, List<ManualChange> after) {
    }

    /**
     * The steps that can be undone, the most recent last; the history starts when the project is opened.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient Deque<HistoryStep> undoSteps = new ArrayDeque<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient Deque<HistoryStep> redoSteps = new ArrayDeque<>();
    /**
     * Counts the changes of the history, so the bindings below are updated.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient IntegerProperty historyVersion = new SimpleIntegerProperty();
    /**
     * The nesting of {@link #runAsOneStep}, the changes of the outermost call are one step.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient int stepDepth;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient List<ManualChange> changesBeforeStep;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient BooleanBinding canUndo;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient BooleanBinding canRedo;
    /**
     * The error of each input that could not be read (or only partly) when the inputs have been read last.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient ReadOnlyObjectWrapper<Map<Input, String>> inputErrorsByInput =
            new ReadOnlyObjectWrapper<>(this, "inputErrorsByInput", Map.of());
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ReadOnlyObjectWrapper<PipelineResult> pipelineResult;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ReadOnlyObjectWrapper<List<String>> inputEpgUrls;
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
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ChangeListener<Boolean> sorterEnabledListener;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient ListChangeListener<Sorter> sorterListListener;

    public DataModel(String name) {
        this(name, null, null, null, null, null, null, null);
    }

    /**
     * Creates a project loaded from a project-file. Lists missing in the project-file are created empty.
     */
    @JsonCreator
    DataModel(@JsonProperty("name") String name,
              @JsonProperty("inputs") List<Input> inputs,
              @JsonProperty("filters") List<Filter> filters,
              @JsonProperty("sorters") List<Sorter> sorters,
              @JsonProperty("enrichers") List<Enricher> enrichers,
              @JsonProperty("outputs") List<Output> outputs,
              @JsonProperty("epgUrls") List<String> epgUrls,
              @JsonProperty("manualChanges") List<ManualChange> manualChanges) {
        this.name = name;
        this.inputs = observableListOf(inputs);
        this.filters = observableListOf(filters);
        sortFilters(this.filters);
        this.sorters = observableListOf(sorters);
        keepOneSorterEnabled(this.sorters);
        this.enrichers = observableListOf(enrichers);
        this.outputs = observableListOf(outputs);
        this.epgUrls = observableListOf(epgUrls);
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
        inputEpgUrls = new ReadOnlyObjectWrapper<>(this, "inputEpgUrls", List.of());
        epgUrls.addListener((ListChangeListener<String>) change -> changed.set(true));
        // the 'dirty'-flag is set with each change of a persisted list, or the enabled-state of one of its plugins
        pluginEnabledListener = (observable, oldValue, newValue) -> changed.set(true);
        pluginListListener = this::onPluginListChanged;
        inputsRecomputeListener = change -> reloadInputs();
        sorterEnabledListener = (observable, wasEnabled, enabled) -> {
            if (enabled) {
                sorters.stream().filter(sorter -> sorter.getEnabled() == observable).findFirst()
                        .ifPresent(this::onSorterEnabled);
            }
        };
        sorterListListener = change -> {
            while (change.next()) {
                change.getRemoved().forEach(sorter -> sorter.getEnabled().removeListener(sorterEnabledListener));
                change.getAddedSubList().forEach(sorter -> sorter.getEnabled().addListener(sorterEnabledListener));
            }
            keepOneSorterEnabled(sorters);
        };
        observePluginList(inputs);
        observePluginList(filters);
        observePluginList(sorters);
        observeSorters();
        observePluginList(enrichers);
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
        changed.set(true);
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
        sortFilters(this.filters);
        observePluginList(this.filters);
    }

    public void setSorters(ObservableList<Sorter> sorters) {
        stopObservingPluginList(this.sorters);
        this.sorters.removeListener(sorterListListener);
        this.sorters.forEach(sorter -> sorter.getEnabled().removeListener(sorterEnabledListener));
        this.sorters = sorters;
        keepOneSorterEnabled(this.sorters);
        observePluginList(this.sorters);
        observeSorters();
    }

    /**
     * Brings the filters into the order they are applied in, see {@link Filter#order()}; filters of the same order
     * keep their relative order.
     */
    public static void sortFilters(List<Filter> filters) {
        List<Filter> sorted = filters.stream().sorted(Comparator.comparingInt(Filter::order)).toList();
        if (!sorted.equals(filters)) {
            if (filters instanceof ObservableList<Filter> observable) {
                observable.setAll(sorted);
            } else {
                filters.clear();
                filters.addAll(sorted);
            }
        }
    }

    /**
     * Sorters sort completely, so only the last one applied would take effect: at most one sorter is enabled.
     * Of several enabled sorters (e.g. in older project files) the last one is kept, as it took effect.
     */
    private static void keepOneSorterEnabled(List<Sorter> sorters) {
        Sorter last = null;
        for (Sorter sorter : sorters) {
            if (sorter.getEnabled().get()) {
                last = sorter;
            }
        }
        for (Sorter sorter : sorters) {
            if (sorter != last) {
                sorter.getEnabled().set(false);
            }
        }
    }

    /**
     * Enabling a sorter disables the other ones.
     */
    private void observeSorters() {
        sorters.forEach(sorter -> sorter.getEnabled().addListener(sorterEnabledListener));
        sorters.addListener(sorterListListener);
    }

    private void onSorterEnabled(Sorter enabledSorter) {
        for (Sorter sorter : sorters) {
            if (sorter != enabledSorter) {
                sorter.getEnabled().set(false);
            }
        }
    }

    public void setEnrichers(ObservableList<Enricher> enrichers) {
        stopObservingPluginList(this.enrichers);
        this.enrichers = enrichers;
        observePluginList(this.enrichers);
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

    /**
     * Applies the enabled enrichers to the sorted entries in the background.
     */
    public void enrichSortedEntries() {
        recompute(Step.ENRICH);
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
        List<Filter> enabledFilters = filters.stream().filter(filter -> filter.getEnabled().get())
                .sorted(Comparator.comparingInt(Filter::order)).toList();
        List<Sorter> enabledSorters = sorters.stream().filter(sorter -> sorter.getEnabled().get()).toList();
        List<Enricher> enabledEnrichers = enrichers.stream().filter(enricher -> enricher.getEnabled().get()).toList();
        List<Entry> rawSnapshot = new ArrayList<>(rawEntryList);
        List<Entry> filteredSnapshot = new ArrayList<>(filteredEntryList);
        // the results of the steps not re-computed are taken over from the previous computation (immutable)
        PipelineResult previous = pipelineResult.get();
        List<ManualChange> changesSnapshot = List.copyOf(manualChanges);
        List<String> previousInputEpgUrls = inputEpgUrls.get();
        log.debug("re-computing entries of project '{}' starting with step {}", name, step);

        getBackgroundExecutorOrDefault().execute(() -> {
            try {
                Map<Input, String> inputErrors = new LinkedHashMap<>();
                Map<Entry, Input> sources = new IdentityHashMap<>();
                Map<Plugin, StepResult> steps = new IdentityHashMap<>();
                Map<Entry, Filter> removedBy = new IdentityHashMap<>();
                List<Entry> raw;
                List<String> loadedEpgUrls;
                if (step == Step.LOAD_INPUTS) {
                    Set<String> epgUrlsOfInputs = new LinkedHashSet<>();
                    raw = loadEntries(inputsSnapshot, inputErrors, sources, epgUrlsOfInputs);
                    loadedEpgUrls = List.copyOf(epgUrlsOfInputs);
                } else {
                    raw = rawSnapshot;
                    loadedEpgUrls = previousInputEpgUrls;
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
                List<Entry> sorted;
                if (step.compareTo(Step.ENRICH) < 0) {
                    sorted = sortEntries(enabledSorters, filtered, steps);
                } else {
                    sorted = previous.sorted();
                    previous.steps().forEach((plugin, stepResult) -> {
                        if (plugin instanceof Sorter) {
                            steps.put(plugin, stepResult);
                        }
                    });
                }
                Map<Entry, Entry> origins = new IdentityHashMap<>();
                List<Attachment> attachments = new ArrayList<>();
                Set<String> enricherEpgUrls = new LinkedHashSet<>();
                List<String> enricherWarnings = new ArrayList<>();
                List<Entry> enriched = enrichEntries(enabledEnrichers, sorted, steps, origins, attachments,
                        enricherEpgUrls, enricherWarnings);
                List<Entry> playlist = ManualChange.applyAll(enriched, changesSnapshot);
                Result result = new Result(new PipelineResult(raw, filtered, sorted, enriched, sources, steps,
                        removedBy, origins, attachments, List.copyOf(enricherEpgUrls)), playlist,
                        changesSnapshot.size(), inputErrors, loadedEpgUrls, enricherWarnings);
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
                inputEpgUrls.set(result.inputEpgUrls());
                inputErrors = describe(result.inputErrors());
                // a property of its own: the result of the pipeline may be equal although the errors changed
                inputErrorsByInput.set(Collections.unmodifiableMap(new LinkedHashMap<>(result.inputErrors())));
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
            reportError(Messages.get("model.error.inputs", String.join("\n", describe(result.inputErrors()))));
        }
        if (!result.enricherWarnings().isEmpty()) {
            reportError(Messages.get("model.error.enrichers", String.join("\n", result.enricherWarnings())));
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
        runAsOneStep(() -> {
            for (Entry entry : List.copyOf(entries)) {
                int index = indexOfSortedEntry(entry);
                if (index >= 0) {
                    sortedEntryList.remove(index);
                    recordManualChange(ManualChange.remove(entry));
                }
            }
        });
    }

    /**
     * Adds the entry to the end of the playlist (the sorted entries).
     * Must be called on the JavaFX application thread.
     */
    public void addEntry(Entry entry) {
        runAsOneStep(() -> {
            sortedEntryList.add(entry);
            recordManualChange(ManualChange.add(entry));
        });
    }

    /**
     * Adds the entries to the playlist at the given position, as one step of the history (e.g. pasted entries).
     *
     * @param position the index the first entry gets, the entries are added at the end if it is beyond the end
     */
    public void insertEntries(List<Entry> entries, int position) {
        if (entries.isEmpty()) {
            return;
        }
        runAsOneStep(() -> {
            int end = sortedEntryList.size();
            entries.forEach(this::addEntry);
            // an entry is added at the end, then moved to the position
            if (position < end) {
                moveEntries(entries, position);
            }
        });
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
        runAsOneStep(() -> {
            recordManualChange(ManualChange.edit(EntryKey.of(entry), updated));
            sortedEntryList.set(index, updated);
        });
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
        runAsOneStep(() -> {
            moved.forEach(entry -> sortedEntryList.remove(indexOfSortedEntry(entry)));
            int index = Math.clamp(targetIndex, 0, sortedEntryList.size());
            sortedEntryList.addAll(index, moved);
            for (int i = index; i < index + moved.size(); i++) {
                recordManualChange(ManualChange.move(sortedEntryList.get(i), i == 0 ? null : sortedEntryList.get(i - 1)));
            }
        });
    }

    /**
     * Discards all changes the user made to the playlist and re-computes it.
     * Must be called on the JavaFX application thread.
     */
    public void resetManualChanges() {
        if (manualChanges.isEmpty()) {
            return;
        }
        // can be undone like any other change made by hand
        runAsOneStep(manualChanges::clear);
        changed.set(true);
        sortFilteredEntries();
    }

    /**
     * Runs changes of the playlist made by hand as one step of the history, e.g. adding and moving pasted entries:
     * they are undone and redone together. Calls may be nested, the outermost one is the step.
     */
    public void runAsOneStep(Runnable changes) {
        if (stepDepth++ == 0) {
            changesBeforeStep = List.copyOf(manualChanges);
        }
        try {
            changes.run();
        } finally {
            if (--stepDepth == 0) {
                List<ManualChange> after = List.copyOf(manualChanges);
                if (!after.equals(changesBeforeStep)) {
                    undoSteps.push(new HistoryStep(changesBeforeStep, after));
                    // a new change replaces the changes undone before
                    redoSteps.clear();
                    historyVersion.set(historyVersion.get() + 1);
                }
                changesBeforeStep = null;
            }
        }
    }

    /**
     * @return true if a change of the playlist made by hand can be undone; not while the entries are re-computed,
     * as the result being computed contains the change
     */
    public BooleanBinding canUndoProperty() {
        if (canUndo == null) {
            canUndo = Bindings.createBooleanBinding(() -> !undoSteps.isEmpty() && !busy.get(), historyVersion, busy);
        }
        return canUndo;
    }

    public BooleanBinding canRedoProperty() {
        if (canRedo == null) {
            canRedo = Bindings.createBooleanBinding(() -> !redoSteps.isEmpty() && !busy.get(), historyVersion, busy);
        }
        return canRedo;
    }

    /**
     * Reverts the last change of the playlist made by hand (or the last step of several changes).
     * Must be called on the JavaFX application thread.
     */
    public void undo() {
        if (!canUndoProperty().get()) {
            return;
        }
        HistoryStep step = undoSteps.pop();
        redoSteps.push(step);
        applyHistory(step.before());
    }

    /**
     * Applies the last change undone again.
     */
    public void redo() {
        if (!canRedoProperty().get()) {
            return;
        }
        HistoryStep step = redoSteps.pop();
        undoSteps.push(step);
        applyHistory(step.after());
    }

    private void applyHistory(List<ManualChange> changes) {
        manualChanges.setAll(changes);
        sortedEntryList.setAll(ManualChange.applyAll(pipelineResult.get().enriched(), manualChanges));
        changed.set(true);
        historyVersion.set(historyVersion.get() + 1);
    }

    private void recordManualChange(ManualChange change) {
        manualChanges.add(change);
        changed.set(true);
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
        reportError(Messages.get("model.error.pipeline", e.getMessage()));
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
        Map<Input, String> errorsByInput = new LinkedHashMap<>();
        List<Entry> entries = loadEntries(inputs, errorsByInput, new IdentityHashMap<>(), new LinkedHashSet<>());
        errors.addAll(describe(errorsByInput));
        return entries;
    }

    /**
     * Reads all given inputs, remembering the input each entry has been read from in {@code sources}
     * and collecting the EPG urls found in the playlist headers in {@code epgUrls}.
     */
    private List<Entry> loadEntries(List<Input> inputs, Map<Input, String> errors, Map<Entry, Input> sources,
                                    Set<String> epgUrls) {
        List<Entry> loadedEntries = new ArrayList<>();
        for (Input currentInput : inputs) {
            log.debug("reading from: {}", currentInput.getName());
            try {
                addEntries(currentInput, currentInput.read(), loadedEntries, sources, epgUrls);
            } catch (InputReadException e) {
                // a part of the input could not be read: the entries of the other parts are kept
                log.error("Error reading input {} ({}): {}", currentInput.getName(), currentInput.getSource(),
                        e.getMessage(), e);
                addEntries(currentInput, e.getEntriesRead(), loadedEntries, sources, epgUrls);
                errors.put(currentInput, e.getMessage());
            } catch (Exception e) {
                log.error("Error reading input {} ({})", currentInput.getName(), currentInput.getSource(), e);
                errors.put(currentInput, UncaughtExceptionHandler.describe(e));
            }
        }
        log.debug("{} raw entries loaded", loadedEntries.size());
        return loadedEntries;
    }

    private static void addEntries(Input input, List<Entry> entries, List<Entry> loadedEntries,
                                   Map<Entry, Input> sources, Set<String> epgUrls) {
        loadedEntries.addAll(entries);
        if (entries instanceof EntryList entryList) {
            epgUrls.addAll(entryList.getEpgUrls());
        }
        entries.forEach(entry -> sources.putIfAbsent(entry, input));
    }

    /**
     * @return e.g. "FritzBox Input (base-url: …): tvhd.m3u: status-code 404", one per input
     */
    private static List<String> describe(Map<Input, String> errors) {
        return errors.entrySet().stream().map(error -> "%s (%s): %s".formatted(error.getKey().getName(),
                error.getKey().getSource(), error.getValue())).toList();
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
        List<String> epgUrlsSnapshot = getEffectiveEpgUrls();
        List<Attachment> attachments = pipelineResult.get().attachments();
        getBackgroundExecutorOrDefault().execute(() -> {
            List<String> errors = new ArrayList<>();
            List<ExportFile> files = new ArrayList<>();
            try {
                files.addAll(createFiles(attachments, playlist, errors));
                writeToOutputs(outputsSnapshot, new Export(playlist, epgUrlsSnapshot, files), errors);
            } finally {
                files.forEach(file -> deleteQuietly(file.content()));
                getFxExecutorOrDefault().execute(() -> {
                    saving.set(false);
                    if (!errors.isEmpty()) {
                        reportError(Messages.get("model.error.outputs", String.join("\n", errors)));
                    }
                });
            }
        });
    }

    /**
     * Creates the content of the additional files once for all outputs, as temporary files.
     * A file that cannot be created is reported and left out, the playlist is written anyway.
     */
    private static List<ExportFile> createFiles(List<Attachment> attachments, List<Entry> playlist, List<String> errors) {
        List<ExportFile> files = new ArrayList<>();
        for (Attachment attachment : attachments) {
            Path content = null;
            try {
                content = Files.createTempFile("m3uedit-", attachment.suffix());
                try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(content))) {
                    attachment.content().write(playlist, outputStream);
                }
                files.add(new ExportFile(attachment.name(), attachment.suffix(), attachment.headerAttributes(), content));
            } catch (IOException | RuntimeException e) {
                log.error("Error creating {}", attachment.name(), e);
                errors.add("%s: %s".formatted(attachment.name(), UncaughtExceptionHandler.describe(e)));
                deleteQuietly(content);
            }
        }
        return files;
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary file {} could not be deleted: {}", file, e.getMessage());
        }
    }

    private void writeToOutputs(List<Output> outputs, Export export, List<String> errors) {
        for (Output output : outputs) {
            log.debug("saving {} entries and {} files to output {}", export.playlist().size(), export.files().size(),
                    output.getName());
            try {
                output.write(export);
            } catch (RuntimeException e) {
                log.error("Error writing to output {} ({})", output.getName(), output.getOutput(), e);
                errors.add("%s: %s".formatted(output.getName(), UncaughtExceptionHandler.describe(e)));
            }
        }
    }

    /**
     * @return the EPG urls found in the headers of the inputs when they have been loaded last
     */
    public ReadOnlyObjectProperty<List<String>> inputEpgUrlsProperty() {
        return inputEpgUrls.getReadOnlyProperty();
    }

    public List<String> getInputEpgUrls() {
        return inputEpgUrls.get();
    }

    /**
     * @return the EPG urls written to the playlist header (unless replaced by an additional file of an enricher):
     * the ones set for the project; if none are set, those of the enrichers; if there are none, those of the inputs
     */
    public List<String> getEffectiveEpgUrls() {
        if (!epgUrls.isEmpty()) {
            return List.copyOf(epgUrls);
        }
        List<String> enricherEpgUrls = getPipelineResult().epgUrls();
        return enricherEpgUrls.isEmpty() ? getInputEpgUrls() : enricherEpgUrls;
    }

    /**
     * @return true while the playlist is written to the outputs in the background
     */
    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    /**
     * @return true if the project contains changes that have not been saved yet
     */
    public boolean isChanged() {
        return changed.get();
    }

    public void setChanged(boolean changed) {
        this.changed.set(changed);
    }

    public BooleanProperty changedProperty() {
        return changed;
    }

    public boolean isSaving() {
        return saving.get();
    }

    /**
     * @return the error of the given input when the inputs have been read last (also if only a part of it could not
     * be read), null if it could be read
     */
    public String getInputError(Input input) {
        return inputErrorsByInput.get().get(input);
    }

    /**
     * @return the error of each input that could not be read (or only partly) when the inputs have been read last
     */
    public ReadOnlyObjectProperty<Map<Input, String>> inputErrorsByInputProperty() {
        return inputErrorsByInput.getReadOnlyProperty();
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

    /**
     * Applies the enrichers in their order. An entry changed by an enricher is a new object, its origin (the sorted
     * entry it has been derived from, possibly by several enrichers) is recorded in {@code origins}.
     */
    private static List<Entry> enrichEntries(List<Enricher> enrichers, List<Entry> entries, Map<Plugin, StepResult> steps,
                                             Map<Entry, Entry> origins, List<Attachment> attachments,
                                             Set<String> epgUrls, List<String> warnings) {
        List<Entry> enrichedEntries = List.copyOf(entries);
        for (Enricher enricher : enrichers) {
            List<Entry> input = enrichedEntries;
            Enrichment enrichment = enricher.enrich(input);
            List<Entry> output = enrichment.entries();
            checkEnrichment(enricher, input, output);
            for (int i = 0; i < output.size(); i++) {
                if (output.get(i) != input.get(i)) {
                    origins.put(output.get(i), origins.getOrDefault(input.get(i), input.get(i)));
                }
            }
            steps.put(enricher, StepResult.ofEnrichment(input, output, enrichment.notes(), enrichment.summary()));
            attachments.addAll(enrichment.attachments());
            epgUrls.addAll(enrichment.epgUrls());
            enrichment.warnings().forEach(warning -> warnings.add("%s: %s".formatted(enricher.getName(), warning)));
            enrichedEntries = output;
        }
        return enrichedEntries;
    }

    /**
     * Enrichers must keep the entries, their order and their identifying name and channel-uri, see {@link Enricher}.
     */
    private static void checkEnrichment(Enricher enricher, List<Entry> input, List<Entry> output) {
        if (output.size() != input.size()) {
            throw new IllegalStateException("%s returned %d instead of %d entries".formatted(enricher.getName(),
                    output.size(), input.size()));
        }
        for (int i = 0; i < output.size(); i++) {
            Entry before = input.get(i);
            Entry after = output.get(i);
            if (after != before && (!Objects.equals(before.getName(), after.getName())
                    || !Objects.equals(before.getChannelUri(), after.getChannelUri()))) {
                throw new IllegalStateException("%s must not change the name or url of entry '%s'".formatted(
                        enricher.getName(), before.getName()));
            }
        }
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
        return "DataModel[name=%s, projectFile=%s, inputs=%d, filters=%d, sorters=%d, enrichers=%d, outputs=%d]"
                .formatted(name, projectFile, inputs.size(), filters.size(), sorters.size(), enrichers.size(),
                        outputs.size());
    }
}

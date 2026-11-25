package org.bitpunk.m3uedit.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.control.PluginStageView;
import org.bitpunk.m3uedit.control.PluginStageView.Badge;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.*;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;

import java.net.URI;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The pipeline of a project, top to bottom: inputs &rarr; filters &rarr; sorters &rarr; enrichers &rarr; outputs.
 * Between the stages, the number of entries passed on is shown. Each plugin shows its effect, e.g. the number of
 * entries removed by a filter. The plugin selected here is shown in detail by the {@link DiffController}.
 */
@Slf4j
public class PipelineController extends VBox implements ModelAware {
    private static final String ICON_WRITE = "M7 2h2v6.2l2.3-2.3 1.4 1.4L8 12 3.3 7.3l1.4-1.4L7 8.2zM2 13h12v1.5H2z";
    private static final String ICON_FLOW = "M7 1h2v10.2l2.3-2.3 1.4 1.4L8 15 3.3 10.3l1.4-1.4L7 11.2z";

    /**
     * The plugin selected in any stage, null if none.
     */
    @Getter
    private final ObjectProperty<Plugin> selectedStep = new SimpleObjectProperty<>(this, "selectedStep");
    @Getter
    private PluginStageView<Input> inputStage;
    @Getter
    private PluginStageView<Filter> filterStage;
    @Getter
    private PluginStageView<Sorter> sorterStage;
    @Getter
    private PluginStageView<Enricher> enricherStage;
    @Getter
    private PluginStageView<Output> outputStage;
    @Getter
    private final Button writeButton = new Button(Messages.get("pipeline.write"));
    /**
     * Tells why the playlist cannot be written (a disabled button shows no tooltip).
     */
    @Getter
    private final Label writeHint = flowLabel();
    /**
     * The button to write the playlist with its hint; not part of the pipeline, so it can stay visible while the
     * pipeline is scrolled.
     */
    @Getter
    private final VBox writeBar = new VBox(writeButton, writeHint);
    private final Label loadedLabel = flowLabel();
    private final Label filteredLabel = flowLabel();
    private final Label sortedLabel = flowLabel();
    private final Label playlistLabel = flowLabel();
    private DataModel dataModel;
    /**
     * Set while all plugins of a stage are enabled or disabled at once, so the entries are re-computed only once.
     */
    private boolean changingAll;
    /**
     * The connectors between the stages, showing the number of entries passed on.
     */
    private List<HBox> connectors = List.of();

    public PipelineController() {
        getStyleClass().add("pipeline");
        setSpacing(0);
    }

    @Override
    public void initModel(DataModel dataModel) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Data model already initialized");
        }
        this.dataModel = dataModel;

        inputStage = new PluginStageView<>(Messages.get("pipeline.stage.inputs"),
                Messages.get("pipeline.stage.inputs.hint"), dataModel.getInputs(), selectedStep, false,
                Input::getSource);
        inputStage.setAddAction(InputPluginService.getInstance().getDescriptors(), this::addInput);
        // the inputs are reloaded by the data model with each change of its list
        inputStage.setOnConfigured(input -> dataModel.reloadInputs());

        // the order of filters is fixed (see Filter#order), sorters sort completely, so only one is enabled
        filterStage = new PluginStageView<>(Messages.get("pipeline.stage.filters"),
                Messages.get("pipeline.stage.filters.hint"), dataModel.getFilters(), selectedStep, true, false,
                Plugin::getDescription);
        filterStage.setOnConfigured(filter -> dataModel.filterRawEntries());

        sorterStage = new PluginStageView<>(Messages.get("pipeline.stage.sorters"),
                Messages.get("pipeline.stage.sorters.hint"), dataModel.getSorters(), selectedStep, true, false,
                Plugin::getDescription);
        sorterStage.setOnConfigured(sorter -> dataModel.sortFilteredEntries());

        enricherStage = new PluginStageView<>(Messages.get("pipeline.stage.enrichers"),
                Messages.get("pipeline.stage.enrichers.hint"), dataModel.getEnrichers(), selectedStep, true,
                Plugin::getDescription);
        enricherStage.setOnConfigured(enricher -> dataModel.enrichSortedEntries());

        filterStage.setEnableAllAction(enabled -> setAllEnabled(dataModel.getFilters(), enabled,
                dataModel::filterRawEntries));
        enricherStage.setEnableAllAction(enabled -> setAllEnabled(dataModel.getEnrichers(), enabled,
                dataModel::enrichSortedEntries));

        outputStage = new PluginStageView<>(Messages.get("pipeline.stage.outputs"),
                Messages.get("pipeline.stage.outputs.hint"), dataModel.getOutputs(), selectedStep, false,
                output -> describe(output.getOutput()));
        outputStage.setAddAction(OutputPluginService.getInstance().getDescriptors(), this::addOutput);

        writeButton.setGraphic(icon(ICON_WRITE));
        writeButton.getStyleClass().add("write-button");
        writeButton.setMaxWidth(Double.MAX_VALUE);
        writeButton.setOnAction(event -> dataModel.saveEntries());
        // writing needs an output and entries; it runs in the background, so starting it twice is prevented
        BooleanBinding noOutput = Bindings.isEmpty(dataModel.getOutputs());
        BooleanBinding noEntries = Bindings.isEmpty(dataModel.getSortedEntryList());
        writeButton.disableProperty().bind(dataModel.savingProperty().or(noOutput).or(noEntries));
        writeHint.textProperty().bind(Bindings.when(noOutput).then(Messages.get("pipeline.write.hint.noOutput"))
                .otherwise(Bindings.when(noEntries).then(Messages.get("pipeline.write.hint.noEntries"))
                        .otherwise("")));
        writeHint.getStyleClass().add("write-hint");
        writeHint.setWrapText(true);
        writeHint.visibleProperty().bind(noOutput.or(noEntries));
        writeHint.managedProperty().bind(writeHint.visibleProperty());

        HBox sortedConnector = connector(sortedLabel);
        connectors = List.of(connector(loadedLabel), connector(filteredLabel), sortedConnector, connector(playlistLabel));
        getChildren().setAll(inputStage, connectors.get(0), filterStage, connectors.get(1),
                sorterStage, connectors.get(2), enricherStage, connectors.get(3), outputStage);
        writeBar.getStyleClass().add("write-bar");
        // the stage is only shown if there are enrichers (none are installed yet in older setups)
        for (Node node : List.of(sortedConnector, enricherStage)) {
            node.visibleProperty().bind(Bindings.isNotEmpty(dataModel.getEnrichers()));
            node.managedProperty().bind(node.visibleProperty());
        }
        // a changed configuration is a change of the project, e.g. asked about before closing it
        for (PluginStageView<?> stage : stages()) {
            stage.setPersistedState(ProjectPersisterUtil::persistedState);
            stage.setOnConfigurationChanged(plugin -> dataModel.setChanged(true));
        }
        // each stage has its own colour (project.css) and is numbered in the order of the pipeline
        inputStage.getStyleClass().add("stage-inputs");
        filterStage.getStyleClass().add("stage-filters");
        sorterStage.getStyleClass().add("stage-sorters");
        enricherStage.getStyleClass().add("stage-enrichers");
        outputStage.getStyleClass().add("stage-outputs");
        enricherStage.visibleProperty().addListener((observable, wasVisible, visible) -> updateStageNumbers());
        updateStageNumbers();

        initRecomputeListeners();
        dataModel.pipelineResultProperty().addListener((observable, oldResult, newResult) -> refresh());
        // e.g. an input read successfully again: the entries, and so the result, may be the same as before
        dataModel.inputErrorsByInputProperty().addListener((observable, oldErrors, newErrors) -> refresh());
        dataModel.getSortedEntryList().addListener((ListChangeListener<Entry>) change -> {
            updateFlowLabels();
            // e.g. an entry added by the user
            updateInactive();
        });
        refresh();
    }

    /**
     * Enables or disables all given plugins and re-computes the entries once.
     */
    private void setAllEnabled(List<? extends Plugin> plugins, boolean enabled, Runnable recompute) {
        changingAll = true;
        try {
            plugins.forEach(plugin -> plugin.getEnabled().set(enabled));
        } finally {
            changingAll = false;
        }
        recompute.run();
    }

    private void recomputeUnlessChangingAll(Runnable recompute) {
        if (!changingAll) {
            recompute.run();
        }
    }

    /**
     * Numbers the stages shown, without gaps: the enricher stage is hidden if there are no enrichers.
     */
    private void updateStageNumbers() {
        int number = 0;
        for (PluginStageView<?> stage : stages()) {
            stage.setNumber(stage.isVisible() ? ++number : 0);
        }
    }

    /**
     * @return the stages in the order of the pipeline
     */
    List<PluginStageView<?>> stages() {
        return List.of(inputStage, filterStage, sorterStage, enricherStage, outputStage);
    }

    private void initRecomputeListeners() {
        // re-compute the entries once per change of a filter or sorter: enabled-state or order
        for (Filter filter : dataModel.getFilters()) {
            filter.getEnabled().addListener((observable, oldValue, newValue) ->
                    recomputeUnlessChangingAll(dataModel::filterRawEntries));
        }
        for (Sorter sorter : dataModel.getSorters()) {
            sorter.getEnabled().addListener((observable, oldValue, newValue) ->
                    recomputeUnlessChangingAll(dataModel::sortFilteredEntries));
        }
        for (Enricher enricher : dataModel.getEnrichers()) {
            enricher.getEnabled().addListener((observable, oldValue, newValue) ->
                    recomputeUnlessChangingAll(dataModel::enrichSortedEntries));
        }
        dataModel.getFilters().addListener((ListChangeListener<Filter>) change -> dataModel.filterRawEntries());
        dataModel.getSorters().addListener((ListChangeListener<Sorter>) change -> dataModel.sortFilteredEntries());
        dataModel.getEnrichers().addListener((ListChangeListener<Enricher>) change -> dataModel.enrichSortedEntries());
    }

    private void refresh() {
        PipelineResult result = dataModel.getPipelineResult();
        Map<Input, Integer> entriesPerInput = new IdentityHashMap<>();
        result.raw().forEach(entry -> {
            Input input = result.sources().get(entry);
            if (input != null) {
                entriesPerInput.merge(input, 1, Integer::sum);
            }
        });
        inputStage.setBadgeProvider(input -> inputBadge(input, entriesPerInput.getOrDefault(input, 0)));
        filterStage.setBadgeProvider(stepBadge(result, step -> step.removedCount() > 0
                ? new Badge("−" + step.removedCount(), "badge-removed", Messages.get("pipeline.filter.badge.removed", step.removedCount()))
                : new Badge("±0", "badge-zero", Messages.get("pipeline.filter.badge.nothing"))));
        sorterStage.setBadgeProvider(stepBadge(result, step -> new Badge("⇅ " + step.movedCount(),
                step.movedCount() > 0 ? "badge-moved" : "badge-zero", Messages.get("pipeline.sorter.badge.moved", step.movedCount()))));
        enricherStage.setBadgeProvider(stepBadge(result, step -> {
            String tooltip = step.modifiedCount() > 0
                    ? Messages.get("pipeline.enricher.badge.changed", step.modifiedCount())
                    : Messages.get("pipeline.enricher.badge.nothing");
            tooltip = step.summary() == null ? tooltip : tooltip + "\n" + step.summary();
            return step.modifiedCount() > 0 ? new Badge("✎ " + step.modifiedCount(), "badge-modified", tooltip)
                    : new Badge("±0", "badge-zero", tooltip);
        }));
        int loaded = result.raw().size();
        updateInactive();
        long failedInputs = dataModel.getInputs().stream().filter(input -> dataModel.getInputError(input) != null)
                .count();
        inputStage.setStatusProvider(() -> Messages.get(failedInputs > 0 ? "pipeline.inputs.statusWithErrors"
                : "pipeline.inputs.status", dataModel.getInputs().size(), loaded, failedInputs));
        filterStage.setStatusProvider(() -> Messages.get("pipeline.filters.status", enabledCount(dataModel.getFilters()),
                dataModel.getFilters().size(), loaded - result.filtered().size()));
        int reordered = EntryDiff.compute(result.filtered(), result.sorted()).getMovedCount();
        sorterStage.setStatusProvider(() -> dataModel.getSorters().stream().filter(sorter -> sorter.getEnabled().get())
                .findFirst().map(sorter -> Messages.get("pipeline.sorters.status", sorter.getName(), reordered))
                .orElse(Messages.get("pipeline.sorters.status.none")));
        int changed = result.origins().size();
        enricherStage.setStatusProvider(() -> Messages.get("pipeline.enrichers.status",
                enabledCount(dataModel.getEnrichers()), dataModel.getEnrichers().size(), changed));
        outputStage.setStatusProvider(() -> dataModel.getOutputs().isEmpty()
                ? Messages.get("pipeline.outputs.status.none")
                : Messages.get("pipeline.outputs.status", dataModel.getOutputs().size()));
        inputStage.refresh();
        filterStage.refresh();
        sorterStage.refresh();
        enricherStage.refresh();
        outputStage.refresh();
        updateFlowLabels();
    }

    /**
     * Greys out the stages that have nothing to work on: filters, sorters and enrichers while no entries have been
     * read (there are no inputs, they could not be read or did not deliver any entries, or they are still being
     * read for the first time), the outputs while the playlist is empty.
     */
    private void updateInactive() {
        // filters, sorters and enrichers only work on the entries read: entries added by the user are added to the
        // playlist after them
        boolean noEntriesRead = dataModel.getPipelineResult().raw().isEmpty();
        for (PluginStageView<?> stage : List.of(filterStage, sorterStage, enricherStage)) {
            stage.setInactive(noEntriesRead);
        }
        for (HBox connector : connectors.subList(0, connectors.size() - 1)) {
            connector.pseudoClassStateChanged(PluginStageView.INACTIVE, noEntriesRead);
        }
        // the outputs write the playlist, also one created by hand without inputs
        boolean emptyPlaylist = dataModel.getSortedEntryList().isEmpty();
        outputStage.setInactive(emptyPlaylist, Messages.get(noEntriesRead
                ? "pipeline.outputs.inactive.noEntriesRead" : "pipeline.outputs.inactive"));
        connectors.getLast().pseudoClassStateChanged(PluginStageView.INACTIVE, emptyPlaylist);
    }

    /**
     * @return the number of entries read from the input, marked as error if the input (or a part of it) could not
     * be read, the error is shown by the tooltip
     */
    private Badge inputBadge(Input input, int entriesRead) {
        String error = dataModel.getInputError(input);
        if (error == null) {
            return new Badge("+" + entriesRead, "badge-count", Messages.get("pipeline.input.badge.read", entriesRead));
        }
        String tooltip = entriesRead > 0
                ? Messages.get("pipeline.input.badge.partly", entriesRead, error)
                : Messages.get("pipeline.input.badge.failed", error);
        return new Badge("⚠ +" + entriesRead, "badge-error", tooltip);
    }

    private static long enabledCount(List<? extends Plugin> plugins) {
        return plugins.stream().filter(plugin -> plugin.getEnabled().get()).count();
    }

    private static <T extends Plugin> Function<T, Badge> stepBadge(PipelineResult result, Function<StepResult, Badge> badge) {
        return plugin -> result.step(plugin).map(badge).orElse(null);
    }

    private void updateFlowLabels() {
        PipelineResult result = dataModel.getPipelineResult();
        loadedLabel.setText(Messages.get("pipeline.flow.loaded", result.raw().size()));
        filteredLabel.setText(Messages.get("pipeline.flow.filtered", result.filtered().size()));
        sortedLabel.setText(Messages.get("pipeline.flow.sorted", result.sorted().size()));
        playlistLabel.setText(Messages.get("pipeline.flow.playlist", dataModel.getSortedEntryList().size()));
    }

    /**
     * Creates the input chosen in the add-menu and adds it, once it has been configured.
     */
    private void addInput(PluginDescriptor<Input> plugin) {
        Input input = plugin.create();
        input.configure();
        if (input.getSource() != null) {
            dataModel.getInputs().add(input);
            selectedStep.set(input);
        } else {
            log.debug("adding input {} cancelled", plugin.id());
        }
    }

    /**
     * Creates the output chosen in the add-menu and adds it, once it has been configured.
     */
    private void addOutput(PluginDescriptor<Output> plugin) {
        Output output = plugin.create();
        output.configure();
        if (output.getOutput() != null) {
            dataModel.getOutputs().add(output);
        } else {
            log.debug("adding output {} cancelled", plugin.id());
        }
    }

    private static String describe(URI uri) {
        if (uri == null) {
            return Messages.get("pipeline.output.notConfigured");
        }
        return "file".equals(uri.getScheme()) ? uri.getPath() : uri.toString();
    }

    private static Label flowLabel() {
        Label label = new Label();
        label.getStyleClass().add("flow-label");
        return label;
    }

    private static HBox connector(Label label) {
        HBox connector = new HBox(8, icon(ICON_FLOW), label);
        connector.setAlignment(Pos.CENTER_LEFT);
        connector.getStyleClass().add("pipeline-connector");
        return connector;
    }

    private static SVGPath icon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("icon");
        return icon;
    }
}

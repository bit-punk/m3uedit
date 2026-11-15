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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.control.PluginStageView;
import org.bitpunk.m3uedit.control.PluginStageView.Badge;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.EntryDiff;
import org.bitpunk.m3uedit.model.PipelineResult;
import org.bitpunk.m3uedit.model.StepResult;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputFactory;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.OutputFactory;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;

import java.net.URI;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The pipeline of a project, top to bottom: inputs &rarr; filters &rarr; sorters &rarr; enrichers &rarr; outputs.
 * Between the stages, the number of entries passed on is shown. Each plugin shows its effect, e.g. the number of
 * entries removed by a filter. The plugin selected here is shown in detail by the {@link DiffController}.
 */
@Slf4j
public class PipelineController extends VBox implements ModelAware {
    private static final String ICON_WRITE = "M7 2h2v6.2l2.3-2.3 1.4 1.4L8 12 3.3 7.3l1.4-1.4L7 8.2zM2 13h12v1.5H2z";
    private static final String ICON_EPG = "M2 3h12v8H2zm1.5 1.5v5h9v-5zM5 12.5h6V14H5z";
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
    private final Button writeButton = new Button("Write playlist");
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
    @Getter
    private final Label epgLabel = new Label();
    @Getter
    private final Button epgButton = new Button("Edit");
    /**
     * Asks the user for the EPG urls of the project, given the current ones and those of the inputs;
     * empty if cancelled.
     */
    @Setter
    private BiFunction<List<String>, List<String>, Optional<List<String>>> epgUrlEditor =
            (epgUrls, inputEpgUrls) -> new EpgUrlDialog(epgUrls, inputEpgUrls).showAndWait();
    private DataModel dataModel;
    /**
     * Set while all plugins of a stage are enabled or disabled at once, so the entries are re-computed only once.
     */
    private boolean changingAll;

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

        inputStage = new PluginStageView<>("Inputs", "are merged", dataModel.getInputs(), selectedStep, false,
                Input::getSource);
        inputStage.setAddAction(InputPluginService.getInstance().getAllPluginNames(), this::addInput);
        // the inputs are reloaded by the data model with each change of its list
        inputStage.setOnConfigured(input -> dataModel.reloadInputs());

        // the order of filters is fixed (see Filter#order), sorters sort completely, so only one is enabled
        filterStage = new PluginStageView<>("Filters", "applied top to bottom", dataModel.getFilters(), selectedStep,
                true, false, Plugin::getDescription);
        filterStage.setOnConfigured(filter -> dataModel.filterRawEntries());

        sorterStage = new PluginStageView<>("Sorters", "one at a time", dataModel.getSorters(), selectedStep,
                true, false, Plugin::getDescription);
        sorterStage.setOnConfigured(sorter -> dataModel.sortFilteredEntries());

        enricherStage = new PluginStageView<>("Enrichers", "complete the entries, add files", dataModel.getEnrichers(),
                selectedStep, true, Plugin::getDescription);
        enricherStage.setOnConfigured(enricher -> dataModel.enrichSortedEntries());

        filterStage.setEnableAllAction(enabled -> setAllEnabled(dataModel.getFilters(), enabled,
                dataModel::filterRawEntries));
        enricherStage.setEnableAllAction(enabled -> setAllEnabled(dataModel.getEnrichers(), enabled,
                dataModel::enrichSortedEntries));

        outputStage = new PluginStageView<>("Outputs", "receive the same playlist", dataModel.getOutputs(),
                selectedStep, false, output -> describe(output.getOutput()));
        outputStage.setAddAction(OutputPluginService.getInstance().getAllPluginNames(), this::addOutput);

        writeButton.setGraphic(icon(ICON_WRITE));
        writeButton.getStyleClass().add("write-button");
        writeButton.setMaxWidth(Double.MAX_VALUE);
        writeButton.setOnAction(event -> dataModel.saveEntries());
        // writing needs an output and entries; it runs in the background, so starting it twice is prevented
        BooleanBinding noOutput = Bindings.isEmpty(dataModel.getOutputs());
        BooleanBinding noEntries = Bindings.isEmpty(dataModel.getSortedEntryList());
        writeButton.disableProperty().bind(dataModel.savingProperty().or(noOutput).or(noEntries));
        writeHint.textProperty().bind(Bindings.when(noOutput).then("Add an output to write the playlist")
                .otherwise(Bindings.when(noEntries).then("The playlist is empty, there is nothing to write")
                        .otherwise("")));
        writeHint.getStyleClass().add("write-hint");
        writeHint.setWrapText(true);
        writeHint.visibleProperty().bind(noOutput.or(noEntries));
        writeHint.managedProperty().bind(writeHint.visibleProperty());

        HBox sortedConnector = connector(sortedLabel);
        getChildren().setAll(inputStage, connector(loadedLabel), filterStage, connector(filteredLabel),
                sorterStage, sortedConnector, enricherStage, connector(playlistLabel), outputStage, epgRow());
        writeBar.getStyleClass().add("write-bar");
        // the stage is only shown if there are enrichers (none are installed yet in older setups)
        for (Node node : List.of(sortedConnector, enricherStage)) {
            node.visibleProperty().bind(Bindings.isNotEmpty(dataModel.getEnrichers()));
            node.managedProperty().bind(node.visibleProperty());
        }

        initRecomputeListeners();
        dataModel.pipelineResultProperty().addListener((observable, oldResult, newResult) -> refresh());
        dataModel.getSortedEntryList().addListener((ListChangeListener<Entry>) change -> updateFlowLabels());
        dataModel.getEpgUrls().addListener((ListChangeListener<String>) change -> updateEpgLabel());
        dataModel.inputEpgUrlsProperty().addListener((observable, oldUrls, newUrls) -> updateEpgLabel());
        dataModel.pipelineResultProperty().addListener((observable, oldResult, newResult) -> updateEpgLabel());
        refresh();
        updateEpgLabel();
    }

    /**
     * The EPG urls written to the header of the playlist, the same for all outputs.
     */
    private HBox epgRow() {
        Label title = new Label("EPG");
        title.getStyleClass().add("stage-title");
        epgLabel.getStyleClass().add("plugin-detail");
        epgLabel.setMinWidth(0);
        epgLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(epgLabel, Priority.ALWAYS);
        epgButton.setAccessibleText("Edit EPG URLs");
        // only the urls are shortened if the pipeline is narrow
        title.setMinWidth(Region.USE_PREF_SIZE);
        epgButton.setMinWidth(Region.USE_PREF_SIZE);
        epgButton.setOnAction(event -> editEpgUrls());
        HBox row = new HBox(6, icon(ICON_EPG), title, epgLabel, epgButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("epg-row");
        return row;
    }

    private void editEpgUrls() {
        List<String> enricherEpgUrls = dataModel.getPipelineResult().epgUrls();
        epgUrlEditor.apply(List.copyOf(dataModel.getEpgUrls()),
                        enricherEpgUrls.isEmpty() ? dataModel.getInputEpgUrls() : enricherEpgUrls)
                .filter(urls -> !urls.equals(dataModel.getEpgUrls()))
                .ifPresent(urls -> dataModel.getEpgUrls().setAll(urls));
    }

    private void updateEpgLabel() {
        List<String> urls = dataModel.getEffectiveEpgUrls();
        String text;
        if (urls.isEmpty()) {
            text = "none";
        } else if (dataModel.getEpgUrls().isEmpty()) {
            boolean fromEnrichers = !dataModel.getPipelineResult().epgUrls().isEmpty();
            text = (fromEnrichers ? "from enrichers: " : "from inputs: ") + String.join(", ", urls);
        } else {
            text = String.join(", ", urls);
        }
        epgLabel.setText(text);
        epgLabel.setTooltip(new Tooltip(urls.isEmpty()
                ? "No EPG URL is written to the playlist, players show no programme guide."
                : "Written to the playlist header (x-tvg-url):\n" + String.join("\n", urls)));
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
        inputStage.setBadgeProvider(input -> new Badge("+" + entriesPerInput.getOrDefault(input, 0), "badge-count",
                "%d entries read".formatted(entriesPerInput.getOrDefault(input, 0))));
        filterStage.setBadgeProvider(stepBadge(result, step -> step.removedCount() > 0
                ? new Badge("−" + step.removedCount(), "badge-removed", "%d entries removed".formatted(step.removedCount()))
                : new Badge("±0", "badge-zero", "nothing removed")));
        sorterStage.setBadgeProvider(stepBadge(result, step -> new Badge("⇅ " + step.movedCount(),
                step.movedCount() > 0 ? "badge-moved" : "badge-zero", "%d entries moved".formatted(step.movedCount()))));
        enricherStage.setBadgeProvider(stepBadge(result, step -> {
            String tooltip = step.modifiedCount() > 0 ? "%d entries changed".formatted(step.modifiedCount())
                    : "nothing changed";
            tooltip = step.summary() == null ? tooltip : tooltip + "\n" + step.summary();
            return step.modifiedCount() > 0 ? new Badge("✎ " + step.modifiedCount(), "badge-modified", tooltip)
                    : new Badge("±0", "badge-zero", tooltip);
        }));
        int loaded = result.raw().size();
        inputStage.setStatusProvider(() -> "%s, %s".formatted(count(dataModel.getInputs().size(), "input"),
                count(loaded, "entry", "entries")));
        filterStage.setStatusProvider(() -> "%d of %d enabled, −%s".formatted(enabledCount(dataModel.getFilters()),
                dataModel.getFilters().size(), count(loaded - result.filtered().size(), "entry", "entries")));
        int reordered = EntryDiff.compute(result.filtered(), result.sorted()).getMovedCount();
        sorterStage.setStatusProvider(() -> dataModel.getSorters().stream().filter(sorter -> sorter.getEnabled().get())
                .findFirst().map(sorter -> "%s, ⇅ %s".formatted(sorter.getName(), count(reordered, "entry", "entries")))
                .orElse("not sorted"));
        int changed = result.origins().size();
        enricherStage.setStatusProvider(() -> "%d of %d enabled, ✎ %s".formatted(enabledCount(dataModel.getEnrichers()),
                dataModel.getEnrichers().size(), count(changed, "entry", "entries")));
        outputStage.setStatusProvider(() -> dataModel.getOutputs().isEmpty() ? "no output"
                : count(dataModel.getOutputs().size(), "output"));
        inputStage.refresh();
        filterStage.refresh();
        sorterStage.refresh();
        enricherStage.refresh();
        outputStage.refresh();
        updateFlowLabels();
    }

    private static long enabledCount(List<? extends Plugin> plugins) {
        return plugins.stream().filter(plugin -> plugin.getEnabled().get()).count();
    }

    private static String count(int count, String singular) {
        return count(count, singular, singular + "s");
    }

    private static String count(int count, String singular, String plural) {
        return "%d %s".formatted(count, count == 1 ? singular : plural);
    }

    private static <T extends Plugin> Function<T, Badge> stepBadge(PipelineResult result, Function<StepResult, Badge> badge) {
        return plugin -> result.step(plugin).map(badge).orElse(null);
    }

    private void updateFlowLabels() {
        PipelineResult result = dataModel.getPipelineResult();
        loadedLabel.setText("%d entries loaded".formatted(result.raw().size()));
        filteredLabel.setText("%d left after filtering".formatted(result.filtered().size()));
        sortedLabel.setText("%d entries sorted".formatted(result.sorted().size()));
        playlistLabel.setText("%d entries in the playlist".formatted(dataModel.getSortedEntryList().size()));
    }

    private void addInput(String inputName) {
        Input input = InputFactory.getInput(inputName);
        input.configure();
        if (input.getSource() != null) {
            dataModel.getInputs().add(input);
            selectedStep.set(input);
        } else {
            log.debug("adding input {} cancelled", inputName);
        }
    }

    private void addOutput(String outputName) {
        Output output = OutputFactory.getOutput(outputName);
        if (output == null) {
            return;
        }
        output.configure();
        if (output.getOutput() != null) {
            dataModel.getOutputs().add(output);
        } else {
            log.debug("adding output {} cancelled", outputName);
        }
    }

    private static String describe(URI uri) {
        if (uri == null) {
            return "not configured";
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

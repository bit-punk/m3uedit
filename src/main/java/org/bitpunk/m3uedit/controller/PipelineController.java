package org.bitpunk.m3uedit.controller;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import lombok.Getter;
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
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.InputFactory;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.OutputFactory;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The pipeline of a project, top to bottom: inputs &rarr; filters &rarr; sorters &rarr; outputs.
 * Between the stages, the number of entries passed on is shown. Each plugin shows its effect, e.g. the number of
 * entries removed by a filter. The plugin selected here is shown in detail by the {@link DiffController}.
 */
public class PipelineController extends VBox implements ModelAware {
    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);
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
    private PluginStageView<Output> outputStage;
    @Getter
    private final Button writeButton = new Button("Write playlist");
    private final Label loadedLabel = flowLabel();
    private final Label filteredLabel = flowLabel();
    private final Label playlistLabel = flowLabel();
    private DataModel dataModel;

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

        filterStage = new PluginStageView<>("Filters", "applied top to bottom", dataModel.getFilters(), selectedStep,
                true, Plugin::getDescription);
        filterStage.setOnConfigured(filter -> dataModel.filterRawEntries());

        sorterStage = new PluginStageView<>("Sorters", "the last one decides", dataModel.getSorters(), selectedStep,
                true, Plugin::getDescription);
        sorterStage.setOnConfigured(sorter -> dataModel.sortFilteredEntries());

        outputStage = new PluginStageView<>("Outputs", "receive the same playlist", dataModel.getOutputs(),
                selectedStep, false, output -> describe(output.getOutput()));
        outputStage.setAddAction(OutputPluginService.getInstance().getAllPluginNames(), this::addOutput);

        writeButton.setGraphic(icon(ICON_WRITE));
        writeButton.getStyleClass().add("write-button");
        writeButton.setMaxWidth(Double.MAX_VALUE);
        writeButton.setOnAction(event -> dataModel.saveEntries());
        // writing to the outputs runs in the background, prevent starting it twice
        writeButton.disableProperty().bind(dataModel.savingProperty());

        getChildren().setAll(inputStage, connector(loadedLabel), filterStage, connector(filteredLabel),
                sorterStage, connector(playlistLabel), outputStage, writeButton);

        initRecomputeListeners();
        dataModel.pipelineResultProperty().addListener((observable, oldResult, newResult) -> refresh());
        dataModel.getSortedEntryList().addListener((ListChangeListener<Entry>) change -> updateFlowLabels());
        refresh();
    }

    private void initRecomputeListeners() {
        // re-compute the entries once per change of a filter or sorter: enabled-state or order
        for (Filter filter : dataModel.getFilters()) {
            filter.getEnabled().addListener((observable, oldValue, newValue) -> dataModel.filterRawEntries());
        }
        for (Sorter sorter : dataModel.getSorters()) {
            sorter.getEnabled().addListener((observable, oldValue, newValue) -> dataModel.sortFilteredEntries());
        }
        dataModel.getFilters().addListener((ListChangeListener<Filter>) change -> dataModel.filterRawEntries());
        dataModel.getSorters().addListener((ListChangeListener<Sorter>) change -> dataModel.sortFilteredEntries());
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
        int loaded = result.raw().size();
        inputStage.setStatusProvider(() -> "%s, %s".formatted(count(dataModel.getInputs().size(), "input"),
                count(loaded, "entry", "entries")));
        filterStage.setStatusProvider(() -> "%d of %d enabled, −%s".formatted(enabledCount(dataModel.getFilters()),
                dataModel.getFilters().size(), count(loaded - result.filtered().size(), "entry", "entries")));
        int reordered = EntryDiff.compute(result.filtered(), result.sorted()).getMovedCount();
        sorterStage.setStatusProvider(() -> "%d of %d enabled, ⇅ %s".formatted(enabledCount(dataModel.getSorters()),
                dataModel.getSorters().size(), count(reordered, "entry", "entries")));
        outputStage.setStatusProvider(() -> dataModel.getOutputs().isEmpty() ? "no output"
                : count(dataModel.getOutputs().size(), "output"));
        inputStage.refresh();
        filterStage.refresh();
        sorterStage.refresh();
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

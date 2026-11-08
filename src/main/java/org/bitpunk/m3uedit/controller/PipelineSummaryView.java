package org.bitpunk.m3uedit.controller;

import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.EntryDiff;
import org.bitpunk.m3uedit.model.PipelineResult;
import org.bitpunk.m3uedit.model.StepResult;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.filter.Filter;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a project above the playlist: loaded &rarr; removed by filters &rarr; in the playlist, and a bar
 * showing the share of the entries removed by each filter. Clicking a filter in the bar or the legend selects it.
 */
public class PipelineSummaryView extends VBox {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass DIMMED = PseudoClass.getPseudoClass("dimmed");

    @Getter
    private final Label loadedFigure = figure();
    @Getter
    private final Label removedFigure = figure();
    @Getter
    private final Label playlistFigure = figure();
    @Getter
    private final Label movedFigure = figure();
    private final GridPane bar = new GridPane();
    @Getter
    private final FlowPane legend = new FlowPane(14, 4);
    private DataModel dataModel;
    private ObjectProperty<Plugin> selectedStep;

    public PipelineSummaryView() {
        getStyleClass().add("pipeline-summary");
        setSpacing(8);
        removedFigure.getStyleClass().add("removed");
        movedFigure.getStyleClass().add("moved");
        HBox flow = new HBox(6,
                loadedFigure, caption("loaded"), arrow(),
                removedFigure, caption("by filters"), arrow(),
                playlistFigure, caption("in the playlist"), spacer(),
                movedFigure, caption("reordered"));
        flow.setAlignment(Pos.BASELINE_LEFT);
        bar.getStyleClass().add("summary-bar");
        bar.setHgap(2);
        bar.setMinHeight(12);
        bar.setPrefHeight(12);
        bar.setMaxHeight(12);
        legend.getStyleClass().add("summary-legend");
        getChildren().addAll(flow, bar, legend);
    }

    /**
     * @param selectedStep the step selected in the pipeline, set when clicking a filter
     */
    public void initModel(DataModel dataModel, ObjectProperty<Plugin> selectedStep) {
        this.dataModel = dataModel;
        this.selectedStep = selectedStep;
        dataModel.pipelineResultProperty().addListener((observable, oldResult, newResult) -> refresh());
        dataModel.getSortedEntryList().addListener((ListChangeListener<Entry>) change -> refresh());
        dataModel.getFilters().addListener((ListChangeListener<Filter>) change -> refresh());
        selectedStep.addListener((observable, oldStep, newStep) -> refresh());
        refresh();
    }

    void refresh() {
        PipelineResult result = dataModel.getPipelineResult();
        int loaded = result.raw().size();
        int playlist = dataModel.getSortedEntryList().size();
        loadedFigure.setText(Integer.toString(loaded));
        removedFigure.setText("−" + (loaded - result.filtered().size()));
        playlistFigure.setText(Integer.toString(playlist));
        int reordered = result.filtered().isEmpty() ? 0
                : EntryDiff.compute(result.filtered(), result.sorted()).getMovedCount();
        movedFigure.setText(Integer.toString(reordered));

        record Segment(Filter filter, int removed) {
        }
        List<Segment> segments = new ArrayList<>();
        for (Filter filter : dataModel.getFilters()) {
            int removed = result.step(filter).map(StepResult::removedCount).orElse(0);
            if (removed > 0) {
                segments.add(new Segment(filter, removed));
            }
        }

        bar.getChildren().clear();
        bar.getColumnConstraints().clear();
        List<Node> legendItems = new ArrayList<>();
        int total = Math.max(1, loaded);
        int kept = result.filtered().size();
        if (kept > 0 || segments.isEmpty()) {
            addSegment(kept > 0 ? kept : 1, total, "segment-kept", "%d entries kept".formatted(kept), null);
            legendItems.add(legendItem("kept " + kept, "segment-kept", null));
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            String style = "segment-removed-" + Math.min(i, 4);
            String text = "%s −%d".formatted(segment.filter().getName(), segment.removed());
            addSegment(segment.removed(), total, style, text, segment.filter());
            legendItems.add(legendItem(text, style, segment.filter()));
        }
        legend.getChildren().setAll(legendItems);
    }

    private void addSegment(int count, int total, String styleClass, String tooltip, Filter filter) {
        Region segment = new Region();
        segment.getStyleClass().addAll("summary-segment", styleClass);
        segment.setMinSize(3, 12);
        segment.setPrefHeight(12);
        Tooltip.install(segment, new Tooltip(tooltip));
        // while a filter is selected, the other segments are dimmed
        boolean filterSelected = selectedStep.get() instanceof Filter;
        segment.pseudoClassStateChanged(DIMMED, filterSelected && selectedStep.get() != filter);
        if (filter != null) {
            segment.getStyleClass().add("clickable");
            segment.setOnMouseClicked(event -> select(filter));
        }
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(100.0 * count / total);
        bar.getColumnConstraints().add(constraints);
        bar.add(segment, bar.getColumnConstraints().size() - 1, 0);
    }

    private Node legendItem(String text, String styleClass, Filter filter) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("legend-swatch", styleClass);
        Label label = new Label(text, swatch);
        label.getStyleClass().add("legend-item");
        if (filter != null) {
            label.getStyleClass().add("clickable");
            label.pseudoClassStateChanged(SELECTED, selectedStep.get() == filter);
            label.setOnMouseClicked(event -> select(filter));
        }
        return label;
    }

    private void select(Filter filter) {
        selectedStep.set(selectedStep.get() == filter ? null : filter);
    }

    private static Label figure() {
        Label label = new Label();
        label.getStyleClass().add("summary-figure");
        return label;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("summary-caption");
        return label;
    }

    private static Label arrow() {
        Label label = new Label("→");
        label.getStyleClass().add("summary-caption");
        return label;
    }

    private static Region spacer() {
        Region spacer = new Region();
        spacer.setMinWidth(16);
        return spacer;
    }
}

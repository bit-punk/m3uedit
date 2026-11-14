package org.bitpunk.m3uedit.controller;

import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.EntryDiff;
import org.bitpunk.m3uedit.model.EntryDiff.Kind;
import org.bitpunk.m3uedit.model.EntryDiff.Row;
import org.bitpunk.m3uedit.model.PipelineResult;
import org.bitpunk.m3uedit.model.StepResult;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.util.EntryMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Side-by-side diff of the entries before and after the step selected in the pipeline, or of all loaded entries
 * and the playlist if no step is selected. Removed entries are shown with the reason for their removal, moved
 * entries with their position on the other side. Unchanged entries can be folded, like in a diff-tool.
 */
public class DiffController extends VBox {
    /**
     * Number of unchanged rows shown around changed ones when folding.
     */
    static final int CONTEXT_ROWS = 2;
    private static final double ROW_HEIGHT = 26;

    /**
     * An item of the diff-list: a row of the diff, or a fold hiding unchanged rows.
     */
    sealed interface Item permits RowItem, FoldItem {
    }

    record RowItem(Row row) implements Item {
    }

    record FoldItem(int start, List<Row> hiddenRows) implements Item {
    }

    /**
     * What is compared: the entries before and after a step.
     *
     * @param origin            returns the entry of the left side an entry of the right side has been derived from
     *                          (by an enricher), the entry itself if unchanged
     * @param changeDescription describes the change of an entry, given the entry before and after the change
     * @param note              a note for an entry of the right side worth mentioning even if it is unchanged
     *                          (e.g. "no EPG data"), null if none; such entries are not folded away
     */
    record Scope(String context, String leftTitle, List<Entry> left, String rightTitle, List<Entry> right,
                 Function<Entry, String> removalReason, Function<Entry, Entry> origin,
                 BiFunction<Entry, Entry, String> changeDescription, Function<Entry, String> note) {

        Scope(String context, String leftTitle, List<Entry> left, String rightTitle, List<Entry> right,
              Function<Entry, String> removalReason) {
            this(context, leftTitle, left, rightTitle, right, removalReason, Function.identity());
        }

        Scope(String context, String leftTitle, List<Entry> left, String rightTitle, List<Entry> right,
              Function<Entry, String> removalReason, Function<Entry, Entry> origin) {
            this(context, leftTitle, left, rightTitle, right, removalReason, origin, EntryMapper::describeChanges,
                    entry -> null);
        }
    }

    @Getter
    private final Label contextLabel = new Label();
    private final Button showAllButton = new Button("Show whole pipeline");
    private final HBox contextBar;
    private final ToggleButton changesOnlyButton = new ToggleButton("Changes only");
    private final ToggleButton allRowsButton = new ToggleButton("All rows");
    @Getter
    private final Label statsLabel = new Label();
    private final TextField searchField = new TextField();
    private final Label leftTitleLabel = new Label();
    private final Label rightTitleLabel = new Label();
    @Getter
    private final ListView<Item> diffListView = new ListView<>();
    private final Label placeholder = new Label();
    private final Set<Integer> expandedFolds = new HashSet<>();
    private DataModel dataModel;
    private ObjectProperty<Plugin> selectedStep;
    private Scope scope;
    @Getter
    private EntryDiff diff;

    public DiffController() {
        getStyleClass().add("diff-view");

        contextLabel.getStyleClass().add("diff-context-label");
        contextLabel.setMaxWidth(Double.MAX_VALUE);
        contextLabel.setWrapText(true);
        HBox.setHgrow(contextLabel, Priority.ALWAYS);
        showAllButton.setOnAction(event -> selectedStep.set(null));
        contextBar = new HBox(10, contextLabel, showAllButton);
        contextBar.setAlignment(Pos.CENTER_LEFT);
        contextBar.getStyleClass().add("diff-context");

        ToggleGroup foldGroup = new ToggleGroup();
        changesOnlyButton.setToggleGroup(foldGroup);
        allRowsButton.setToggleGroup(foldGroup);
        changesOnlyButton.setSelected(true);
        foldGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);    // one of both is always selected
            } else {
                rebuildItems();
            }
        });
        HBox segmented = new HBox(changesOnlyButton, allRowsButton);
        segmented.getStyleClass().add("segmented");
        statsLabel.getStyleClass().add("diff-stats");
        searchField.setPromptText("Search channel");
        searchField.textProperty().addListener((observable, oldText, newText) -> rebuildItems());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(12, segmented, statsLabel, spacer, searchField);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("diff-toolbar");

        HBox columnHeader = new HBox(side(leftTitleLabel), side(rightTitleLabel));
        columnHeader.getStyleClass().add("diff-header");

        diffListView.setFixedCellSize(ROW_HEIGHT);
        diffListView.setCellFactory(listView -> new DiffCell());
        diffListView.setPlaceholder(placeholder);
        diffListView.getStyleClass().add("diff-list");
        VBox.setVgrow(diffListView, Priority.ALWAYS);

        getChildren().addAll(contextBar, toolbar, columnHeader, diffListView);
    }

    private static HBox side(Label title) {
        title.setMaxWidth(Double.MAX_VALUE);
        HBox side = new HBox(title);
        side.getStyleClass().add("diff-header-side");
        HBox.setHgrow(title, Priority.ALWAYS);
        HBox.setHgrow(side, Priority.ALWAYS);
        side.setPrefWidth(0);
        side.setMaxWidth(Double.MAX_VALUE);
        return side;
    }

    /**
     * @param selectedStep the step selected in the pipeline, the whole pipeline is shown if null
     */
    public void initModel(DataModel dataModel, ObjectProperty<Plugin> selectedStep) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Data model already initialized");
        }
        this.dataModel = dataModel;
        this.selectedStep = selectedStep;
        selectedStep.addListener((observable, oldStep, newStep) -> {
            expandedFolds.clear();
            refresh();
        });
        dataModel.pipelineResultProperty().addListener((observable, oldResult, newResult) -> refresh());
        // the playlist may be edited by the user
        dataModel.getSortedEntryList().addListener((ListChangeListener<Entry>) change -> refresh());
        refresh();
    }

    /**
     * Re-computes the diff of the selected step.
     */
    void refresh() {
        scope = computeScope(dataModel.getPipelineResult(), selectedStep.get());
        contextLabel.setText(scope.context());
        showAllButton.setVisible(selectedStep.get() != null);
        showAllButton.setManaged(selectedStep.get() != null);
        contextBar.pseudoClassStateChanged(PseudoClass.getPseudoClass("step"), selectedStep.get() != null);
        leftTitleLabel.setText("%s (%d)".formatted(scope.leftTitle(), scope.left().size()));
        rightTitleLabel.setText("%s (%d)".formatted(scope.rightTitle(), scope.right().size()));
        diff = EntryDiff.compute(scope.left(), scope.right(), scope.origin());
        List<String> stats = new ArrayList<>();
        if (diff.getRemovedCount() > 0) {
            stats.add("−%d removed".formatted(diff.getRemovedCount()));
        }
        if (diff.getMovedCount() > 0) {
            stats.add("↕ %d moved".formatted(diff.getMovedCount()));
        }
        if (diff.getAddedCount() > 0) {
            stats.add("+%d added".formatted(diff.getAddedCount()));
        }
        if (diff.getModifiedCount() > 0) {
            stats.add("✎ %d changed".formatted(diff.getModifiedCount()));
        }
        stats.add("%d unchanged".formatted(diff.getUnchangedCount()));
        statsLabel.setText(String.join("   ", stats));
        rebuildItems();
    }

    private Scope computeScope(PipelineResult result, Plugin step) {
        List<Entry> playlist = List.copyOf(dataModel.getSortedEntryList());
        Function<Entry, String> globalReason = entry -> {
            String reason = result.removalReason(entry);
            return reason != null ? reason : "removed or edited manually";
        };
        if (step instanceof Input input) {
            List<Entry> fromInput = result.entriesOf(input);
            List<Entry> inPlaylist = playlist.stream().filter(entry -> result.sourceOf(entry) == input).toList();
            return new Scope("%s delivers %d entries, %d of them are in the playlist.".formatted(input.getName(),
                    fromInput.size(), inPlaylist.size()), input.getName(), fromInput, "In the playlist", inPlaylist,
                    globalReason, result::originOf);
        }
        if (step instanceof Filter filter) {
            Optional<StepResult> stepResult = result.step(filter);
            if (stepResult.isEmpty()) {
                return disabledScope(filter);
            }
            StepResult filterStep = stepResult.get();
            Function<Entry, String> reason = entry -> {
                String description = filter.describeRemoval(entry, filterStep.input());
                return description != null ? description : "removed";
            };
            return new Scope("%s removes %d of %d entries.".formatted(filter.getName(), filterStep.removedCount(),
                    filterStep.input().size()), "Before", filterStep.input(), "After", filterStep.output(), reason);
        }
        if (step instanceof Sorter sorter) {
            Optional<StepResult> stepResult = result.step(sorter);
            if (stepResult.isEmpty()) {
                return disabledScope(sorter);
            }
            StepResult sorterStep = stepResult.get();
            return new Scope("%s moves %d of %d entries.".formatted(sorter.getName(), sorterStep.movedCount(),
                    sorterStep.input().size()), "Before", sorterStep.input(), "After", sorterStep.output(), entry -> null);
        }
        if (step instanceof Enricher enricher) {
            Optional<StepResult> stepResult = result.step(enricher);
            if (stepResult.isEmpty()) {
                return disabledScope(enricher);
            }
            StepResult enricherStep = stepResult.get();
            Map<Entry, Entry> origins = new IdentityHashMap<>();
            for (int i = 0; i < enricherStep.output().size(); i++) {
                origins.put(enricherStep.output().get(i), enricherStep.input().get(i));
            }
            Map<Entry, String> notes = enricherStep.notes();
            String context = "%s changes %d of %d entries.".formatted(enricher.getName(), enricherStep.modifiedCount(),
                    enricherStep.input().size());
            return new Scope(enricherStep.summary() == null ? context : context + " " + enricherStep.summary(),
                    "Before", enricherStep.input(), "After", enricherStep.output(),
                    entry -> null, entry -> origins.getOrDefault(entry, entry), (before, after) -> {
                String changes = EntryMapper.describeChanges(before, after);
                String reason = enricher.describeChange(before, after);
                if (reason == null) {
                    reason = notes.get(after);
                }
                return reason == null ? changes : changes.isEmpty() ? reason : "%s (%s)".formatted(changes, reason);
            }, notes::get);
        }
        String context = "All steps: the entries loaded from the inputs compared to the playlist."
                + " Select a step of the pipeline to see its effect.";
        return new Scope(context, "Loaded", result.raw(), "Playlist", playlist, globalReason, result::originOf);
    }

    private static Scope disabledScope(Plugin plugin) {
        return new Scope("%s is disabled. Enable it to see its effect.".formatted(plugin.getName()),
                "Before", List.of(), "After", List.of(), entry -> null);
    }

    private void rebuildItems() {
        if (diff == null) {
            return;
        }
        List<Row> rows = diff.getRows();
        List<Item> items = new ArrayList<>(rows.size());
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (!search.isEmpty()) {
            rows.stream().filter(row -> matches(row.left(), search) || matches(row.right(), search))
                    .forEach(row -> items.add(new RowItem(row)));
        } else if (!changesOnlyButton.isSelected()) {
            rows.forEach(row -> items.add(new RowItem(row)));
        } else {
            foldUnchangedRows(rows, items);
        }
        placeholder.setText(scope.left().isEmpty() && scope.right().isEmpty() ? "No entries."
                : !search.isEmpty() ? "No channel matches \"%s\".".formatted(searchField.getText().trim())
                : "No differences.");
        diffListView.getItems().setAll(items);
    }

    private void foldUnchangedRows(List<Row> rows, List<Item> items) {
        int k = 0;
        while (k < rows.size()) {
            if (!isFoldable(rows.get(k))) {
                items.add(new RowItem(rows.get(k)));
                k++;
                continue;
            }
            int start = k;
            while (k < rows.size() && isFoldable(rows.get(k))) {
                k++;
            }
            int head = start == 0 ? 0 : CONTEXT_ROWS;
            int tail = k == rows.size() ? 0 : CONTEXT_ROWS;
            List<Row> run = rows.subList(start, k);
            if (expandedFolds.contains(start) || run.size() <= head + tail + 1) {
                run.forEach(row -> items.add(new RowItem(row)));
                continue;
            }
            run.subList(0, head).forEach(row -> items.add(new RowItem(row)));
            items.add(new FoldItem(start, run.subList(head, run.size() - tail)));
            run.subList(run.size() - tail, run.size()).forEach(row -> items.add(new RowItem(row)));
        }
    }

    private static boolean matches(Entry entry, String search) {
        return entry != null && entry.getName() != null && entry.getName().toLowerCase(Locale.ROOT).contains(search);
    }

    /**
     * Unchanged rows are folded away, unless the scope has a note for their entry.
     */
    private boolean isFoldable(Row row) {
        return row.isUnchanged() && (row.right() == null || scope.note().apply(row.right()) == null);
    }

    void expand(FoldItem fold) {
        expandedFolds.add(fold.start());
        rebuildItems();
    }

    /**
     * Scrolls to the other side of a moved entry, expanding a fold hiding it.
     */
    void showCounterpart(Entry entry, boolean onLeftSide) {
        for (Item item : List.copyOf(diffListView.getItems())) {
            if (item instanceof FoldItem fold && fold.hiddenRows().stream()
                    .anyMatch(row -> isCounterpart(row, entry, onLeftSide))) {
                expand(fold);
                break;
            }
        }
        List<Item> items = diffListView.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof RowItem(Row row) && isCounterpart(row, entry, onLeftSide)) {
                diffListView.scrollTo(Math.max(0, i - 3));
                diffListView.getSelectionModel().select(i);
                return;
            }
        }
    }

    /**
     * @return true if the other side of the row shows the given entry, or the entry it has been derived from
     */
    private boolean isCounterpart(Row row, Entry entry, boolean onLeftSide) {
        if (onLeftSide) {
            return row.right() != null && scope.origin().apply(row.right()) == entry;
        }
        return row.left() != null && row.left() == scope.origin().apply(entry);
    }

    private class DiffCell extends ListCell<Item> {
        private final SideView left = new SideView(true);
        private final SideView right = new SideView(false);
        private final HBox rowBox = new HBox(left, right);
        private final Button foldButton = new Button();

        DiffCell() {
            getStyleClass().add("diff-cell");
            foldButton.getStyleClass().add("diff-fold");
            foldButton.setMaxWidth(Double.MAX_VALUE);
            foldButton.setOnAction(event -> {
                if (getItem() instanceof FoldItem fold) {
                    expand(fold);
                }
            });
            setText(null);
        }

        @Override
        protected void updateItem(Item item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            switch (item) {
                case null -> setGraphic(null);
                case RowItem(Row row) -> {
                    left.show(row.left(), row.leftPosition(), row.leftKind(), row.right());
                    right.show(row.right(), row.rightPosition(), row.rightKind(), row.left());
                    setGraphic(rowBox);
                }
                case FoldItem fold -> {
                    foldButton.setText("⋯ show %d unchanged entries".formatted(fold.hiddenRows().size()));
                    setGraphic(foldButton);
                }
            }
        }
    }

    private class SideView extends HBox {
        private final boolean leftSide;
        private final Label gutter = new Label();
        private final Label position = new Label();
        private final Label name = new Label();
        private final Label note = new Label();
        private Entry entry;
        private Kind kind;

        SideView(boolean leftSide) {
            super(6);
            this.leftSide = leftSide;
            getStyleClass().add("diff-side");
            setAlignment(Pos.CENTER_LEFT);
            gutter.getStyleClass().add("diff-gutter");
            gutter.setMinWidth(14);
            position.getStyleClass().add("diff-position");
            position.setMinWidth(40);
            position.setAlignment(Pos.CENTER_RIGHT);
            name.getStyleClass().add("diff-name");
            name.setMinWidth(40);
            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);
            note.getStyleClass().add("diff-note");
            note.setMinWidth(0);
            note.setMaxWidth(320);
            getChildren().addAll(gutter, position, name, note);
            HBox.setHgrow(this, Priority.ALWAYS);
            setPrefWidth(0);
            setMaxWidth(Double.MAX_VALUE);
            setOnMouseClicked(event -> {
                if (kind == Kind.MOVED && entry != null) {
                    showCounterpart(entry, leftSide);
                }
            });
        }

        /**
         * @param counterpart the entry on the other side of the row, null if none
         */
        void show(Entry entry, int entryPosition, Kind kind, Entry counterpart) {
            this.entry = entry;
            this.kind = kind;
            getStyleClass().removeAll("removed", "moved", "added", "modified", "blank");
            if (entry == null) {
                getStyleClass().add("blank");
                gutter.setText(null);
                position.setText(null);
                name.setText(null);
                note.setText(null);
                note.setTooltip(null);
                return;
            }
            position.setText(Integer.toString(entryPosition));
            name.setText(entry.getName());
            String noteText = null;
            String gutterText = null;
            switch (kind) {
                case REMOVED -> {
                    getStyleClass().add("removed");
                    gutterText = "−";
                    noteText = scope.removalReason().apply(entry);
                }
                case MOVED -> {
                    getStyleClass().add("moved");
                    gutterText = "↕";
                    noteText = leftSide ? "to #%d".formatted(positionInRight(entry))
                            : "from #%d".formatted(positionInLeft(entry));
                }
                case MODIFIED -> {
                    getStyleClass().add("modified");
                    gutterText = "✎";
                    if (!leftSide && counterpart != null) {
                        noteText = scope.changeDescription().apply(counterpart, entry);
                    }
                }
                case ADDED -> {
                    getStyleClass().add("added");
                    gutterText = "+";
                    noteText = "added or edited manually";
                }
                case UNCHANGED -> {
                    if (!leftSide) {
                        noteText = scope.note().apply(entry);
                    }
                }
            }
            gutter.setText(gutterText);
            note.setText(noteText);
            note.setTooltip(noteText != null ? new Tooltip(noteText) : null);
            setCursorForKind(kind);
        }

        private void setCursorForKind(Kind kind) {
            setCursor(kind == Kind.MOVED ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        }
    }

    private int positionInLeft(Entry rightEntry) {
        Entry origin = scope.origin().apply(rightEntry);
        List<Entry> entries = scope.left();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) == origin) {
                return i + 1;
            }
        }
        return 0;
    }

    private int positionInRight(Entry leftEntry) {
        List<Entry> entries = scope.right();
        for (int i = 0; i < entries.size(); i++) {
            if (scope.origin().apply(entries.get(i)) == leftEntry) {
                return i + 1;
            }
        }
        return 0;
    }
}

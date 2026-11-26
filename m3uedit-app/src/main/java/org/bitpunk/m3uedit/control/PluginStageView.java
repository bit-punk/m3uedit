package org.bitpunk.m3uedit.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.plugins.missing.MissingPlugin;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One stage of the pipeline of a project (inputs, filters, sorters or outputs): a header and a row per plugin.
 * <p>
 * Clicking a row selects its plugin (shared by all stages), a double-click configures it. Depending on the stage,
 * plugins can be enabled, reordered (drag &amp; drop or arrow-buttons), added or removed. Plugins of stages whose
 * order is fixed (e.g. filters) can be enabled, but not reordered.
 *
 * @param <T> the plugin type of this stage
 */
public class PluginStageView<T extends Plugin> extends VBox {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass DISABLED_PLUGIN = PseudoClass.getPseudoClass("plugin-disabled");
    private static final PseudoClass MISSING_PLUGIN = PseudoClass.getPseudoClass("plugin-missing");
    private static final PseudoClass DROP_BEFORE = PseudoClass.getPseudoClass("drop-before");
    private static final PseudoClass DROP_AFTER = PseudoClass.getPseudoClass("drop-after");
    private static final PseudoClass COLLAPSED = PseudoClass.getPseudoClass("collapsed");
    /**
     * Set while a number is shown: the rows are indented by its width, so their checkboxes stay aligned with the
     * one of the header.
     */
    private static final PseudoClass NUMBERED = PseudoClass.getPseudoClass("numbered");
    /**
     * Set while the stage has nothing to work on, e.g. no entries have been read from the inputs yet.
     */
    public static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");

    private static final String ICON_CHEVRON = "M4.4 5.6L8 9.2l3.6-3.6L13 7l-5 5-5-5z";
    private static final String ICON_PLUS = "M7 2h2v5h5v2H9v5H7V9H2V7h5z";
    private static final String ICON_UP = "M8 4l5 5-1.4 1.4L8 6.8l-3.6 3.6L3 9z";
    private static final String ICON_DOWN = "M8 12L3 7l1.4-1.4L8 9.2l3.6-3.6L13 7z";
    private static final String ICON_REMOVE = "M4.4 3L8 6.6 11.6 3 13 4.4 9.4 8l3.6 3.6-1.4 1.4L8 9.4 4.4 13 3 11.6 6.6 8 3 4.4z";
    private static final String ICON_CONFIGURE = "M2 4h7v1.5H2zM11 4h3v1.5h-3zM2 10.5h2V12H2zM7 10.5h7V12H7zM9 2.5h2v4.5H9zM4 9h2v4.5H4z";
    private static final String ICON_GRIP = "M5 3h2v2H5zM9 3h2v2H9zM5 7h2v2H5zM9 7h2v2H9zM5 11h2v2H5zM9 11h2v2H9z";

    /**
     * Text shown next to a plugin, e.g. the number of entries removed by a filter.
     */
    public record Badge(String text, String styleClass, String tooltip) {
    }

    private final ObservableList<T> plugins;
    private final ObjectProperty<Plugin> selection;
    private final boolean enableable;
    private final Function<T, String> detailText;
    private final VBox rowBox = new VBox();
    private final Label emptyLabel = new Label();
    /**
     * The position of the stage in the pipeline, shown before its name.
     */
    private final Label numberLabel = new Label();
    private final Tooltip inactiveTooltip = new Tooltip(noEntriesRead());
    private final HBox header;
    private final Label hintLabel;
    private final String title;
    private final String hint;
    @Getter
    private final Button collapseButton;
    private final BooleanProperty collapsed = new SimpleBooleanProperty(this, "collapsed", false);
    private final Map<T, PluginRow> rows = new IdentityHashMap<>();
    /**
     * Returns the status shown in the header while the stage is collapsed, e.g. the number of enabled plugins.
     */
    @Setter
    private Supplier<String> statusProvider = () -> null;
    /**
     * Returns the badge of a plugin, null for none.
     */
    @Setter
    private Function<T, Badge> badgeProvider = plugin -> null;
    /**
     * Called after a plugin has been configured by the user.
     */
    @Setter
    private Consumer<T> onConfigured = plugin -> {};
    /**
     * Called after the configuration of a plugin has been changed by the user (not if the dialog is cancelled or
     * nothing is changed), see {@link #persistedState}.
     */
    @Setter
    private Consumer<T> onConfigurationChanged = plugin -> {};
    /**
     * The state of a plugin as stored in the project, compared before and after it is configured; by default
     * nothing is compared and no change is reported.
     */
    @Setter
    private Function<? super T, String> persistedState = plugin -> null;
    /**
     * Asks the user whether the given plugin may be removed; plugins can only be removed if an add-action is set.
     */
    @Setter
    private Function<T, Boolean> removeConfirmation = PluginStageView::confirmRemoval;
    @Getter
    private MenuButton addButton;
    /**
     * Enables or disables all plugins of the stage at once: checked if all are enabled, unchecked if none,
     * indeterminate otherwise. Only shown if an action is set, see {@link #setEnableAllAction(Consumer)}.
     */
    @Getter
    private CheckBox enableAllCheckBox;
    private Consumer<Boolean> enableAllAction;
    private PluginRow draggedRow;
    /**
     * Whether the plugins can be reordered by the user (only plugins that can be enabled).
     */
    private final boolean reorderable;

    /**
     * Creates a stage whose plugins can be enabled and reordered if {@code orderable} is set.
     *
     * @param title     the name of the stage
     * @param hint      a short explanation shown next to the name
     * @param plugins   the plugins of this stage, changes are shown immediately
     * @param selection the plugin selected in any stage of the pipeline
     * @param orderable whether plugins can be enabled and reordered
     * @param detail    the second line shown for a plugin
     */
    public PluginStageView(String title, String hint, ObservableList<T> plugins, ObjectProperty<Plugin> selection,
                           boolean orderable, Function<T, String> detail) {
        this(title, hint, plugins, selection, orderable, orderable, detail);
    }

    /**
     * @param enableable  whether plugins can be enabled (filters, sorters, enrichers)
     * @param reorderable whether plugins can be reordered (drag &amp; drop or arrow-buttons); only plugins that can
     *                    be enabled
     */
    public PluginStageView(String title, String hint, ObservableList<T> plugins, ObjectProperty<Plugin> selection,
                           boolean enableable, boolean reorderable, Function<T, String> detail) {
        this.plugins = plugins;
        this.selection = selection;
        this.enableable = enableable;
        this.reorderable = enableable && reorderable;
        this.detailText = detail;
        getStyleClass().add("pipeline-stage");

        this.title = title;
        this.hint = hint;
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stage-title");
        // in a narrow pipeline the hint is shortened, the name stays readable
        titleLabel.setMinWidth(Region.USE_PREF_SIZE);
        hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("stage-hint");
        hintLabel.setMinWidth(0);
        HBox.setHgrow(hintLabel, Priority.ALWAYS);
        hintLabel.setMaxWidth(Double.MAX_VALUE);
        collapseButton = iconButton(ICON_CHEVRON, Messages.get("stage.collapse", title));
        collapseButton.getStyleClass().add("stage-collapse");
        collapseButton.setOnAction(event -> collapsed.set(!collapsed.get()));
        numberLabel.getStyleClass().add("stage-number");
        numberLabel.setMinWidth(Region.USE_PREF_SIZE);
        setNumber(0);
        // the number is the leftmost element, before the buttons and the check-box enabling all plugins
        header = new HBox(6, numberLabel, collapseButton, titleLabel, hintLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("stage-header");
        // clicking the header (not one of its buttons) collapses or expands the stage
        header.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isOnControl(event, header)) {
                collapsed.set(!collapsed.get());
            }
        });
        collapsed.addListener((observable, wasCollapsed, isCollapsed) -> updateCollapsed());

        rowBox.getStyleClass().add("stage-rows");
        emptyLabel.setText(Messages.get("stage.empty", titleInText()));
        emptyLabel.getStyleClass().add("stage-empty");
        getChildren().addAll(header, rowBox);

        plugins.addListener((ListChangeListener<T>) change -> rebuildRows());
        selection.addListener((observable, oldValue, newValue) -> rows.values().forEach(PluginRow::updateSelected));
        rebuildRows();
        updateCollapsed();
    }

    /**
     * While collapsed, only the header is shown, with the status of the stage instead of its hint.
     * @param number the position of the stage in the pipeline, shown before its name; 0 to show none
     */
    public void setNumber(int number) {
        numberLabel.setText(Integer.toString(number));
        numberLabel.setVisible(number > 0);
        numberLabel.setManaged(number > 0);
        pseudoClassStateChanged(NUMBERED, number > 0);
    }

    /**
     * @return the position of the stage in the pipeline, 0 if none is shown
     */
    public int getNumber() {
        return numberLabel.isVisible() ? Integer.parseInt(numberLabel.getText()) : 0;
    }

    /**
     * Shows the stage greyed out while it has nothing to work on; its plugins can still be added and configured.
     */
    public void setInactive(boolean inactive) {
        setInactive(inactive, noEntriesRead());
    }

    /**
     * @return the name of the stage within a text, e.g. "filters" in English, "Filter" in German (nouns are
     * capitalised there)
     */
    private String titleInText() {
        return Boolean.parseBoolean(Messages.get("stage.lowerCaseTitleInText"))
                ? title.toLowerCase(Messages.locale()) : title;
    }

    /**
     * @return the reason of a stage being inactive because no entries have been read
     */
    public static String noEntriesRead() {
        return Messages.get("stage.inactive.noEntriesRead");
    }

    /**
     * @param reason why the stage is inactive, shown when hovering its header
     */
    public void setInactive(boolean inactive, String reason) {
        inactiveTooltip.setText(reason);
        pseudoClassStateChanged(INACTIVE, inactive);
        if (inactive) {
            Tooltip.install(header, inactiveTooltip);
        } else {
            Tooltip.uninstall(header, inactiveTooltip);
        }
    }

    public boolean isInactive() {
        return getPseudoClassStates().contains(INACTIVE);
    }

    /**
     * @return why the stage is inactive, see {@link #setInactive(boolean, String)}
     */
    public String getInactiveReason() {
        return isInactive() ? inactiveTooltip.getText() : null;
    }

    public BooleanProperty collapsedProperty() {
        return collapsed;
    }

    public boolean isCollapsed() {
        return collapsed.get();
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed.set(collapsed);
    }

    /**
     * @return the text shown next to the title: the status while collapsed, the hint otherwise
     */
    public String getHeaderText() {
        return hintLabel.getText();
    }

    private void updateCollapsed() {
        boolean isCollapsed = collapsed.get();
        rowBox.setVisible(!isCollapsed);
        rowBox.setManaged(!isCollapsed);
        pseudoClassStateChanged(COLLAPSED, isCollapsed);
        collapseButton.setRotate(isCollapsed ? -90 : 0);
        String action = Messages.get(isCollapsed ? "stage.expand" : "stage.collapse", title);
        collapseButton.setAccessibleText(action);
        collapseButton.getTooltip().setText(action);
        updateStatus();
    }

    private void updateStatus() {
        String status = collapsed.get() ? statusProvider.get() : null;
        hintLabel.setText(status != null ? status : hint);
        hintLabel.pseudoClassStateChanged(COLLAPSED, status != null);
    }

    /**
     * Shows a checkbox in the header enabling or disabling all plugins of this stage (whose plugins can be enabled) at once. A click
     * disables all plugins if all are enabled, and enables all otherwise.
     *
     * @param action enables ({@code true}) or disables ({@code false}) all plugins, so the caller can re-compute
     *               once instead of once per plugin
     */
    public void setEnableAllAction(Consumer<Boolean> action) {
        if (!enableable) {
            throw new IllegalStateException("Plugins of the stage %s cannot be enabled".formatted(title));
        }
        enableAllAction = action;
        if (enableAllCheckBox == null) {
            enableAllCheckBox = new CheckBox();
            enableAllCheckBox.getStyleClass().add("stage-enable-all");
            enableAllCheckBox.setTooltip(new Tooltip());
            enableAllCheckBox.setOnAction(event -> {
                enableAllAction.accept(!areAllEnabled());
                updateEnableAll();
            });
            header.getChildren().add(header.getChildren().indexOf(collapseButton) + 1, enableAllCheckBox);
        }
        updateEnableAll();
    }

    private boolean areAllEnabled() {
        return !plugins.isEmpty() && plugins.stream().allMatch(plugin -> PluginStates.enabled(plugin).get());
    }

    private void updateEnableAll() {
        if (enableAllCheckBox == null) {
            return;
        }
        long enabled = plugins.stream().filter(plugin -> PluginStates.enabled(plugin).get()).count();
        enableAllCheckBox.setIndeterminate(enabled > 0 && enabled < plugins.size());
        enableAllCheckBox.setSelected(!plugins.isEmpty() && enabled == plugins.size());
        enableAllCheckBox.setDisable(plugins.isEmpty());
        String action = Messages.get(areAllEnabled() ? "stage.disableAll" : "stage.enableAll", titleInText());
        enableAllCheckBox.getTooltip().setText(action);
        enableAllCheckBox.setAccessibleText(action);
    }

    /**
     * Shows an add-button offering the given plugins by their names; the selected plugin is passed to {@code onAdd}.
     * Plugins of a stage with an add-button can be removed as well.
     */
    public void setAddAction(List<PluginDescriptor<T>> addablePlugins, Consumer<PluginDescriptor<T>> onAdd) {
        addButton = new MenuButton(null, icon(ICON_PLUS));
        addButton.getStyleClass().add("stage-add");
        addButton.setTooltip(new Tooltip(Messages.get("common.add")));
        for (PluginDescriptor<T> plugin : addablePlugins) {
            MenuItem item = new MenuItem(plugin.name());
            item.setMnemonicParsing(false);
            item.setOnAction(event -> onAdd.accept(plugin));
            addButton.getItems().add(item);
        }
        header.getChildren().add(addButton);
        rows.values().forEach(PluginRow::updateRemovable);
    }

    /**
     * Updates the badges and details of all plugins, e.g. after the entries have been re-computed.
     */
    public void refresh() {
        rows.values().forEach(PluginRow::update);
        updateStatus();
    }

    /**
     * @return the row showing the given plugin
     */
    public Optional<HBox> rowOf(T plugin) {
        return Optional.ofNullable(rows.get(plugin));
    }

    private void rebuildRows() {
        rows.keySet().removeIf(plugin -> {
            boolean removed = indexOf(plugin) < 0;
            if (removed) {
                rows.get(plugin).dispose();
                if (selection.get() == plugin) {
                    selection.set(null);    // a removed plugin must not stay selected
                }
            }
            return removed;
        });
        List<Node> children = new ArrayList<>();
        for (int i = 0; i < plugins.size(); i++) {
            T plugin = plugins.get(i);
            PluginRow row = rows.computeIfAbsent(plugin, PluginRow::new);
            row.updatePosition(i, plugins.size());
            children.add(row);
        }
        if (children.isEmpty()) {
            children.add(emptyLabel);
        }
        rowBox.getChildren().setAll(children);
        updateStatus();
        updateEnableAll();
    }

    private void select(T plugin) {
        selection.set(selection.get() == plugin ? null : plugin);
    }

    private void move(T plugin, int newIndex) {
        int oldIndex = indexOf(plugin);
        if (oldIndex < 0 || newIndex < 0 || newIndex >= plugins.size() || newIndex == oldIndex) {
            return;
        }
        List<T> reordered = new ArrayList<>(plugins);
        reordered.remove(oldIndex);
        reordered.add(newIndex, plugin);
        plugins.setAll(reordered);    // a single change, so the entries are re-computed once
    }

    private int indexOf(T plugin) {
        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i) == plugin) {
                return i;
            }
        }
        return -1;
    }

    private void configure(T plugin) {
        if (!plugin.isConfigurable()) {
            return;
        }
        String before = persistedState.apply(plugin);
        plugin.configure(getScene() == null ? null : getScene().getWindow());
        PluginRow row = rows.get(plugin);
        if (row != null) {
            row.update();
        }
        onConfigured.accept(plugin);
        String after = persistedState.apply(plugin);
        if (after != null && !after.equals(before)) {
            onConfigurationChanged.accept(plugin);
        }
    }

    private void remove(T plugin) {
        if (Boolean.TRUE.equals(removeConfirmation.apply(plugin))) {
            plugins.removeIf(p -> p == plugin);
        }
    }

    private static boolean confirmRemoval(Plugin plugin) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle(Messages.get("common.remove"));
        dialog.setHeaderText(Messages.get("stage.remove.header", plugin.getName()));
        ButtonType remove = new ButtonType(Messages.get("common.remove"), ButtonBar.ButtonData.OK_DONE);
        dialog.getButtonTypes().setAll(remove,
                new ButtonType(Messages.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        return dialog.showAndWait().filter(remove::equals).isPresent();
    }

    private static SVGPath icon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("icon");
        return icon;
    }

    private static Button iconButton(String path, String tooltip) {
        Button button = new Button(null, icon(path));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        return button;
    }

    /**
     * @return whether the event has been triggered on a control of the row (button, checkbox)
     */
    private static boolean isOnControl(MouseEvent event, Node row) {
        Object target = event.getTarget();
        for (Node node = target instanceof Node n ? n : null; node != null && node != row; node = node.getParent()) {
            if (node instanceof ButtonBase) {
                return true;
            }
        }
        return false;
    }

    private class PluginRow extends HBox {
        private final T plugin;
        private final CheckBox enabledCheckBox;
        private final Label titleLabel = new Label();
        private final Label detailLabel = new Label();
        private final Label badgeLabel = new Label();
        private final Button configureButton = iconButton(ICON_CONFIGURE, Messages.get("stage.configure"));
        private final Button upButton = iconButton(ICON_UP, Messages.get("stage.moveUp"));
        private final Button downButton = iconButton(ICON_DOWN, Messages.get("stage.moveDown"));
        private final Button removeButton = iconButton(ICON_REMOVE, Messages.get("common.remove"));
        private final HBox tools = new HBox(1);

        PluginRow(T plugin) {
            super(6);
            this.plugin = plugin;
            getStyleClass().add("plugin-row");
            setAlignment(Pos.CENTER_LEFT);
            setFocusTraversable(true);

            titleLabel.getStyleClass().add("plugin-title");
            detailLabel.getStyleClass().add("plugin-detail");
            VBox text = new VBox(titleLabel, detailLabel);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            badgeLabel.getStyleClass().add("badge");
            badgeLabel.setMinWidth(Region.USE_PREF_SIZE);

            if (enableable) {
                // the width of the grip is set by the style sheet, so the checkboxes of the rows are aligned with
                // the one of the header (which follows the button collapsing the stage)
                StackPane grip = new StackPane(icon(ICON_GRIP));
                grip.getStyleClass().add("grip");
                // without reordering the space of the grip is kept, so the checkboxes stay aligned
                grip.setVisible(reorderable);
                enabledCheckBox = new CheckBox();
                enabledCheckBox.selectedProperty().bindBidirectional(PluginStates.enabled(plugin));
                // a plugin that is not installed does not take part anyway, its state is kept for the project-file
                enabledCheckBox.setDisable(plugin instanceof MissingPlugin);
                enabledCheckBox.setAccessibleText(Messages.get("stage.enable", plugin.getName()));
                getChildren().addAll(grip, enabledCheckBox);
                PluginStates.enabled(plugin).addListener((observable, oldValue, newValue) -> {
                    updateEnabled();
                    updateEnableAll();
                });
                if (reorderable) {
                    tools.getChildren().addAll(upButton, downButton);
                    upButton.setOnAction(event -> move(plugin, indexOf(plugin) - 1));
                    downButton.setOnAction(event -> move(plugin, indexOf(plugin) + 1));
                    initDragAndDrop();
                }
            } else {
                enabledCheckBox = null;
            }
            tools.getChildren().addFirst(configureButton);
            configureButton.setOnAction(event -> configure(plugin));
            removeButton.setOnAction(event -> remove(plugin));
            getChildren().addAll(text, badgeLabel, tools);

            setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || isOnControl(event, this)) {
                    return;
                }
                requestFocus();
                if (event.getClickCount() == 1) {
                    select(plugin);
                } else if (event.getClickCount() == 2) {
                    selection.set(plugin);
                    configure(plugin);
                }
            });
            setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                    select(plugin);
                    event.consume();
                } else if (event.getCode() == KeyCode.DELETE && isRemovable()) {
                    remove(plugin);
                    event.consume();
                }
            });
            update();
            updateRemovable();
            updateSelected();
            updateEnabled();
        }

        void update() {
            titleLabel.setText(plugin.getName());
            String detail = detailText.apply(plugin);
            detailLabel.setText(detail);
            detailLabel.setVisible(detail != null && !detail.isEmpty());
            detailLabel.setManaged(detailLabel.isVisible());
            setTooltipText(plugin.getDescription());
            configureButton.setVisible(plugin.isConfigurable());
            configureButton.setManaged(plugin.isConfigurable());
            Badge badge = plugin instanceof MissingPlugin
                    ? new Badge(Messages.get("missingPlugin.badge"), "badge-error", plugin.getDescription())
                    : badgeProvider.apply(plugin);
            pseudoClassStateChanged(MISSING_PLUGIN, plugin instanceof MissingPlugin);
            badgeLabel.getStyleClass().setAll("badge");
            if (badge == null) {
                badgeLabel.setText(null);
                badgeLabel.setVisible(false);
                badgeLabel.setTooltip(null);
            } else {
                badgeLabel.setText(badge.text());
                badgeLabel.setVisible(true);
                if (badge.styleClass() != null) {
                    badgeLabel.getStyleClass().add(badge.styleClass());
                }
                badgeLabel.setTooltip(badge.tooltip() != null ? new Tooltip(badge.tooltip()) : null);
            }
        }

        private void setTooltipText(String text) {
            Tooltip.install(this, text != null && !text.isEmpty() ? new Tooltip(text) : null);
        }

        void updatePosition(int index, int size) {
            upButton.setDisable(index == 0);
            downButton.setDisable(index == size - 1);
        }

        /**
         * @return true if the plugin can be removed: the plugins of stages with an add-button, and plugins that are
         * not installed (e.g. a filter of another party)
         */
        boolean isRemovable() {
            return addButton != null || plugin instanceof MissingPlugin;
        }

        void updateRemovable() {
            boolean removable = isRemovable();
            removeButton.setVisible(removable);
            removeButton.setManaged(removable);
            if (removable && !tools.getChildren().contains(removeButton)) {
                tools.getChildren().add(removeButton);
            }
        }

        void updateSelected() {
            pseudoClassStateChanged(SELECTED, selection.get() == plugin);
        }

        void updateEnabled() {
            pseudoClassStateChanged(DISABLED_PLUGIN, enableable && !PluginStates.enabled(plugin).get());
        }

        void dispose() {
            if (enabledCheckBox != null) {
                enabledCheckBox.selectedProperty().unbindBidirectional(PluginStates.enabled(plugin));
            }
        }

        private void initDragAndDrop() {
            setOnDragDetected(event -> {
                Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                dragboard.setDragView(snapshot(null, null));
                ClipboardContent content = new ClipboardContent();
                content.putString(plugin.getName());
                dragboard.setContent(content);
                draggedRow = this;
                event.consume();
            });
            setOnDragOver(event -> {
                if (draggedRow != null && draggedRow != this) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    boolean before = event.getY() < getHeight() / 2;
                    pseudoClassStateChanged(DROP_BEFORE, before);
                    pseudoClassStateChanged(DROP_AFTER, !before);
                    event.consume();
                }
            });
            setOnDragExited(event -> clearDropIndicator());
            setOnDragDropped(event -> {
                if (draggedRow != null && draggedRow != this) {
                    boolean before = event.getY() < getHeight() / 2;
                    T dragged = draggedRow.plugin;
                    int target = indexOf(plugin) + (before ? 0 : 1);
                    if (indexOf(dragged) < target) {
                        target--;
                    }
                    move(dragged, target);
                    event.setDropCompleted(true);
                    event.consume();
                }
                clearDropIndicator();
            });
            setOnDragDone(event -> draggedRow = null);
        }

        private void clearDropIndicator() {
            pseudoClassStateChanged(DROP_BEFORE, false);
            pseudoClassStateChanged(DROP_AFTER, false);
        }
    }
}

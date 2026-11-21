package org.bitpunk.m3uedit.controller;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.controller.GuideTimeline.Slot;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.util.ChannelLogos;
import org.bitpunk.m3uedit.plugins.enricher.Programme;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A programme guide like on a TV: a row per channel with its programmes as blocks along a time axis. The channel
 * column and the time axis stay in place while the programmes are scrolled. A vertical line marks the current time,
 * running programmes show their progress. The arrow keys move the selection between the programmes.
 * <p>
 * Clicking the name of a channel, double-clicking a programme or pressing enter plays the channel, see
 * {@link #setOnPlay(Consumer)}.
 */
public class ProgrammeGuideView extends VBox {
    static final double CHANNEL_WIDTH = 200;
    static final double ROW_HEIGHT = 34;
    static final double HEADER_HEIGHT = 28;
    static final double PIXELS_PER_MINUTE = 3.5;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass RUNNING = PseudoClass.getPseudoClass("running");
    private static final PseudoClass DIMMED = PseudoClass.getPseudoClass("dimmed");
    private static final PseudoClass PAST = PseudoClass.getPseudoClass("past");
    private static final PseudoClass ODD = PseudoClass.getPseudoClass("odd");
    private static final DateTimeFormatter TICK = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE dd.MM.");

    /**
     * A channel of the guide.
     *
     * @param name  the name shown
     * @param logo  the url of its logo, null if none
     * @param entry  the entry of the playlist played for this channel, null if none
     * @param number the number of the entry in the playlist, as the table shows it; 0 if none
     */
    public record Channel(String name, String logo, Entry entry, int number) {
        public Channel(String name, String logo) {
            this(name, logo, null, 0);
        }
    }

    private final ZoneId zone;
    @Getter
    private final ObjectProperty<Programme> selectedProgramme = new SimpleObjectProperty<>(this, "selectedProgramme");
    private final Label cornerLabel = new Label();
    private final Pane timeHeader = new Pane();
    private final VBox channelColumn = new VBox();
    private final Pane grid = new Pane();
    private final Region nowLine = new Region();
    private final Region nowMarker = new Region();
    @Getter
    private final ScrollPane scrollPane = new ScrollPane(grid);
    private final Map<Programme, Block> blocks = new IdentityHashMap<>();
    @Getter
    private List<List<Slot>> rows = List.of();
    @Getter
    private List<Channel> channels = List.of();
    /**
     * Plays the entry of a channel, e.g. in the external player.
     */
    @Setter
    private Consumer<Entry> onPlay = entry -> {};
    @Getter
    private GuideTimeline timeline;
    private Instant now = Instant.now();

    /**
     * The block of a programme, with its progress while it is running.
     */
    private static final class Block extends StackPane {
        final Slot slot;
        final int row;
        final Region progress = new Region();

        Block(Slot slot, int row, String text) {
            this.slot = slot;
            this.row = row;
            getStyleClass().add("guide-programme");
            Label label = new Label(text);
            label.getStyleClass().add("guide-programme-title");
            label.setMinWidth(0);
            progress.getStyleClass().add("guide-progress");
            progress.setManaged(false);
            getChildren().addAll(label, progress);
            setAlignment(Pos.CENTER_LEFT);
            setMinWidth(0);
            pseudoClassStateChanged(ODD, row % 2 != 0);
        }
    }

    public ProgrammeGuideView(ZoneId zone) {
        this.zone = zone;
        getStyleClass().add("guide");
        setFocusTraversable(true);

        cornerLabel.getStyleClass().add("guide-corner");
        cornerLabel.setMinWidth(CHANNEL_WIDTH);
        cornerLabel.setPrefWidth(CHANNEL_WIDTH);
        cornerLabel.setMinHeight(HEADER_HEIGHT);
        cornerLabel.setPrefHeight(HEADER_HEIGHT);
        timeHeader.getStyleClass().add("guide-time-header");
        Pane headerClip = clipped(timeHeader);
        // the header keeps its height: the guide below shrinks instead, so the times are never cut off
        headerClip.setMinHeight(HEADER_HEIGHT);
        headerClip.setPrefHeight(HEADER_HEIGHT);
        HBox.setHgrow(headerClip, Priority.ALWAYS);
        HBox header = new HBox(cornerLabel, headerClip);
        header.setMinHeight(HEADER_HEIGHT);

        channelColumn.getStyleClass().add("guide-channels");
        Pane channelClip = clipped(channelColumn);
        channelClip.setMinWidth(CHANNEL_WIDTH);
        channelClip.setPrefWidth(CHANNEL_WIDTH);
        // the channel column and the time axis are not part of the scroll pane, they only follow its scrolling:
        // scrolling over them scrolls the programmes
        channelClip.setOnScroll(event -> {
            scrollByPixels(horizontalDelta(event), event.isShiftDown() ? 0 : event.getDeltaY());
            event.consume();
        });
        headerClip.setOnScroll(event -> {
            // the time axis only moves in time: the mouse wheel scrolls horizontally there
            double delta = horizontalDelta(event);
            scrollByPixels(delta != 0 ? delta : event.getDeltaY(), 0);
            event.consume();
        });
        grid.getStyleClass().add("guide-grid");
        scrollPane.getStyleClass().add("guide-scroll");
        scrollPane.setFocusTraversable(false);
        // the scroll pane takes the focus when it is clicked: it is passed on to the guide in the same pulse, so the
        // focus ring of the scroll pane is never drawn (it shifted the programmes for a moment)
        javafx.beans.value.ChangeListener<Node> focusRedirect = (observable, oldOwner, owner) -> {
            if (owner == scrollPane) {
                requestFocus();
            }
        };
        sceneProperty().addListener((observable, oldScene, scene) -> {
            if (oldScene != null) {
                oldScene.focusOwnerProperty().removeListener(focusRedirect);
            }
            if (scene != null) {
                scene.focusOwnerProperty().addListener(focusRedirect);
            }
        });
        // the keys are used to move the selection, not to scroll
        scrollPane.addEventFilter(KeyEvent.ANY, event -> {
            if (event.getCode().isArrowKey() || event.getCode().isNavigationKey() || event.getCode() == KeyCode.ENTER) {
                if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                    handleKey(event);
                }
                event.consume();
            }
        });
        HBox.setHgrow(scrollPane, Priority.ALWAYS);
        HBox body = new HBox(channelClip, scrollPane);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(header, body);

        nowLine.getStyleClass().add("guide-now-line");
        nowLine.setManaged(false);
        nowMarker.getStyleClass().add("guide-now-marker");
        nowMarker.setManaged(false);
        // the channel column and the time axis follow the scrolled programmes
        scrollPane.hvalueProperty().addListener((observable, oldValue, newValue) -> followScrolling());
        scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> followScrolling());
        scrollPane.viewportBoundsProperty().addListener((observable, oldBounds, bounds) -> followScrolling());
        setOnKeyPressed(this::handleKey);
        selectedProgramme.addListener((observable, oldProgramme, newProgramme) -> {
            Block previous = oldProgramme == null ? null : blocks.get(oldProgramme);
            if (previous != null) {
                previous.pseudoClassStateChanged(SELECTED, false);
            }
            Block selected = newProgramme == null ? null : blocks.get(newProgramme);
            if (selected != null) {
                selected.pseudoClassStateChanged(SELECTED, true);
                scrollIntoView(selected);
            }
        });
    }

    private static Pane clipped(Node content) {
        Pane pane = new Pane(content);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(pane.widthProperty());
        clip.heightProperty().bind(pane.heightProperty());
        pane.setClip(clip);
        pane.setMinSize(0, 0);
        return pane;
    }

    private void followScrolling() {
        timeHeader.setTranslateX(-scrollX());
        channelColumn.setTranslateY(-scrollY());
        if (timeline != null) {
            cornerLabel.setText(DATE.format(timeline.timeAt(scrollX()).atZone(zone)));
        }
    }

    /**
     * @return the horizontal position of the programmes at the left edge of the viewport
     */
    /**
     * Scrolls the programmes by the given distance, like the scroll pane itself does for scrolling over them.
     *
     * @param deltaX positive to show earlier programmes (to the left)
     * @param deltaY positive to show the channels above
     */
    void scrollByPixels(double deltaX, double deltaY) {
        Bounds viewport = scrollPane.getViewportBounds();
        double scrollableWidth = grid.getPrefWidth() - viewport.getWidth();
        double scrollableHeight = grid.getPrefHeight() - viewport.getHeight();
        if (deltaX != 0 && scrollableWidth > 0) {
            scrollPane.setHvalue(Math.clamp(scrollPane.getHvalue() - deltaX / scrollableWidth, 0, 1));
        }
        if (deltaY != 0 && scrollableHeight > 0) {
            scrollPane.setVvalue(Math.clamp(scrollPane.getVvalue() - deltaY / scrollableHeight, 0, 1));
        }
    }

    /**
     * @return the horizontal part of a scroll event: of a touchpad, or the mouse wheel while Shift is pressed
     * (reported as vertical scrolling on some platforms)
     */
    private static double horizontalDelta(ScrollEvent event) {
        if (event.getDeltaX() != 0) {
            return event.getDeltaX();
        }
        return event.isShiftDown() ? event.getDeltaY() : 0;
    }

    double scrollX() {
        double scrollable = grid.getPrefWidth() - scrollPane.getViewportBounds().getWidth();
        return scrollable > 0 ? scrollPane.getHvalue() * scrollable : 0;
    }

    /**
     * @return the vertical position of the programmes at the top edge of the viewport
     */
    double scrollY() {
        double scrollable = grid.getPrefHeight() - scrollPane.getViewportBounds().getHeight();
        return scrollable > 0 ? scrollPane.getVvalue() * scrollable : 0;
    }

    /**
     * Shows the given channels, each with its programmes.
     *
     * @param channels   the channels in the order shown
     * @param programmes the programmes of each channel, in the same order
     */
    public void setChannels(List<Channel> channels, List<List<Programme>> programmes, Instant now) {
        this.now = now;
        this.channels = List.copyOf(channels);
        blocks.clear();
        List<List<Slot>> slotRows = new ArrayList<>();
        programmes.forEach(row -> slotRows.add(GuideTimeline.slots(row)));
        rows = List.copyOf(slotRows);
        timeline = GuideTimeline.covering(rows, zone, PIXELS_PER_MINUTE);

        channelColumn.getChildren().clear();
        grid.getChildren().clear();
        for (int i = 0; i < channels.size(); i++) {
            Node cell = channelCell(channels.get(i));
            cell.pseudoClassStateChanged(ODD, i % 2 != 0);
            channelColumn.getChildren().add(cell);
            Region background = new Region();
            background.getStyleClass().add("guide-row");
            background.pseudoClassStateChanged(ODD, i % 2 != 0);
            background.resizeRelocate(0, i * ROW_HEIGHT, timeline.width(), ROW_HEIGHT);
            background.setManaged(false);
            grid.getChildren().add(background);
            for (Slot slot : rows.get(i)) {
                Block block = block(slot, i);
                blocks.put(slot.programme(), block);
                grid.getChildren().add(block);
            }
        }
        grid.setPrefSize(timeline.width(), Math.max(1, channels.size()) * ROW_HEIGHT);
        grid.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        grid.getChildren().add(nowLine);
        buildTimeHeader();
        setNow(now);
        selectedProgramme.set(null);
        followScrolling();
    }

    private Node channelCell(Channel channel) {
        HBox cell = new HBox(8);
        cell.getStyleClass().add("guide-channel");
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setMinSize(CHANNEL_WIDTH, ROW_HEIGHT);
        cell.setPrefSize(CHANNEL_WIDTH, ROW_HEIGHT);
        cell.setMaxSize(CHANNEL_WIDTH, ROW_HEIGHT);
        if (channel.number() > 0) {
            Label index = new Label(Integer.toString(channel.number()));
            index.getStyleClass().add("guide-channel-index");
            index.setMinWidth(Region.USE_PREF_SIZE);
            index.setPrefWidth(28);
            index.setAlignment(Pos.CENTER_RIGHT);
            cell.getChildren().add(index);
        }
        if (channel.logo() != null && !channel.logo().isBlank()) {
            ImageView logo = new ImageView(ChannelLogos.get(channel.logo(), 40, 24));
            logo.setFitWidth(40);
            logo.setFitHeight(24);
            logo.setPreserveRatio(true);
            cell.getChildren().add(logo);
        }
        Label name = new Label(channel.name());
        name.getStyleClass().add("guide-channel-name");
        name.setMinWidth(0);
        cell.getChildren().add(name);
        if (channel.entry() != null) {
            cell.getStyleClass().add("guide-channel-playable");
            Tooltip.install(cell, new Tooltip("Play %s in the external player".formatted(channel.name())));
            cell.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    onPlay.accept(channel.entry());
                }
            });
        }
        return cell;
    }

    private Block block(Slot slot, int row) {
        Programme programme = slot.programme();
        String title = programme.title() == null ? "" : programme.title();
        Block block = new Block(slot, row, title);
        double x = timeline.x(slot.start());
        double width = Math.max(4, timeline.x(slot.end()) - x - 2);
        block.setManaged(false);
        block.resizeRelocate(x + 1, row * ROW_HEIGHT + 2, width, ROW_HEIGHT - 4);
        String time = "%s – %s".formatted(TICK.format(slot.start().atZone(zone)), TICK.format(slot.end().atZone(zone)));
        Tooltip.install(block, new Tooltip(programme.subTitle() == null ? "%s\n%s".formatted(title, time)
                : "%s – %s\n%s".formatted(title, programme.subTitle(), time)));
        block.setOnMouseClicked(event -> {
            selectedProgramme.set(programme);
            requestFocus();
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                play(row);
            }
        });
        return block;
    }

    private void buildTimeHeader() {
        timeHeader.getChildren().clear();
        for (Instant tick : timeline.ticks()) {
            boolean midnight = tick.atZone(zone).toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
            Label label = new Label(midnight ? DATE.format(tick.atZone(zone)) : TICK.format(tick.atZone(zone)));
            label.getStyleClass().add(midnight ? "guide-tick-day" : "guide-tick");
            // managed: the pane sizes it once its style is known, the position is kept
            label.relocate(timeline.x(tick), 5);
            timeHeader.getChildren().add(label);
        }
        timeHeader.getChildren().add(nowMarker);
        timeHeader.setPrefSize(timeline.width(), HEADER_HEIGHT);
    }

    /**
     * Moves the line of the current time, updates the progress of the running programmes and darkens the past ones.
     */
    public void setNow(Instant now) {
        this.now = now;
        if (timeline == null) {
            return;
        }
        double x = timeline.x(now);
        boolean visible = !now.isBefore(timeline.start()) && now.isBefore(timeline.end());
        nowLine.setVisible(visible);
        nowMarker.setVisible(visible);
        nowLine.resizeRelocate(x - 1, 0, 2, Math.max(ROW_HEIGHT, grid.getPrefHeight()));
        nowMarker.resizeRelocate(x - 1, 0, 2, HEADER_HEIGHT);
        for (Block block : blocks.values()) {
            boolean running = block.slot.isRunningAt(now);
            block.pseudoClassStateChanged(RUNNING, running);
            block.pseudoClassStateChanged(PAST, !block.slot.end().isAfter(now));
            double fraction = running ? (double) java.time.Duration.between(block.slot.start(), now).toSeconds()
                    / java.time.Duration.between(block.slot.start(), block.slot.end()).toSeconds() : 0;
            block.progress.setVisible(running);
            block.progress.resizeRelocate(0, block.getHeight() - 3, Math.max(0, block.getWidth() * fraction), 3);
        }
    }

    /**
     * Dims the programmes not matching the search text (title, sub-title or description), none if it is empty.
     */
    public void setSearch(String search) {
        String text = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        for (Block block : blocks.values()) {
            Programme programme = block.slot.programme();
            boolean matches = text.isEmpty() || contains(programme.title(), text)
                    || contains(programme.subTitle(), text) || contains(programme.description(), text);
            block.pseudoClassStateChanged(DIMMED, !matches);
        }
    }

    private static boolean contains(String value, String text) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(text);
    }

    /**
     * Scrolls horizontally, so the given time is shown near the left edge.
     */
    public void scrollTo(Instant time) {
        if (timeline == null) {
            return;
        }
        double viewportWidth = scrollPane.getViewportBounds().getWidth();
        double scrollable = timeline.width() - viewportWidth;
        if (scrollable > 0) {
            scrollPane.setHvalue(Math.clamp((timeline.x(time) - 60) / scrollable, 0, 1));
        }
    }

    /**
     * Selects the programme of the first channel running at the given time (or the next one).
     */
    public void selectAt(Instant time) {
        for (List<Slot> row : rows) {
            Slot slot = GuideTimeline.slotAt(row, time);
            if (slot != null) {
                selectedProgramme.set(slot.programme());
                return;
            }
        }
    }

    /**
     * @return the node showing the given programme, null if it is not shown
     */
    Node blockOf(Programme programme) {
        return blocks.get(programme);
    }

    /**
     * Plays the channel of the given row.
     */
    private void play(int row) {
        Entry entry = row >= 0 && row < channels.size() ? channels.get(row).entry() : null;
        if (entry != null) {
            onPlay.accept(entry);
        }
    }

    private void handleKey(KeyEvent event) {
        Programme selected = selectedProgramme.get();
        Block block = selected == null ? null : blocks.get(selected);
        if (event.getCode() == KeyCode.ENTER) {
            if (block != null) {
                play(block.row);
            }
            event.consume();
            return;
        }
        if (block == null) {
            selectAt(now);
            event.consume();
            return;
        }
        Slot target = switch (event.getCode()) {
            case LEFT -> neighbour(block, -1);
            case RIGHT -> neighbour(block, 1);
            case UP -> slotNear(block.row - 1, -1, block.slot);
            case DOWN -> slotNear(block.row + 1, 1, block.slot);
            case PAGE_UP -> slotNear(Math.max(0, block.row - visibleRowCount()), -1, block.slot);
            case PAGE_DOWN -> slotNear(Math.min(rows.size() - 1, block.row + visibleRowCount()), 1, block.slot);
            default -> null;
        };
        if (target != null) {
            selectedProgramme.set(target.programme());
            event.consume();
        }
    }

    private int visibleRowCount() {
        return Math.max(1, (int) (scrollPane.getViewportBounds().getHeight() / ROW_HEIGHT));
    }

    private Slot neighbour(Block block, int direction) {
        List<Slot> row = rows.get(block.row);
        int index = row.indexOf(block.slot) + direction;
        return index >= 0 && index < row.size() ? row.get(index) : null;
    }

    /**
     * @return the slot in the given row as {@link #slotInRow}; rows without programmes are skipped in the given
     * direction, then back towards the row of the selection (a page may end on rows without programmes)
     */
    private Slot slotNear(int row, int direction, Slot from) {
        for (int candidate = row; candidate >= 0 && candidate < rows.size(); candidate += direction) {
            Slot slot = slotInRow(candidate, from);
            if (slot != null) {
                return slot;
            }
        }
        for (int candidate = row - direction; candidate >= 0 && candidate < rows.size(); candidate -= direction) {
            Slot slot = slotInRow(candidate, from);
            if (slot != null) {
                return slot;
            }
        }
        return null;
    }

    /**
     * @return the slot of another row running at the start of the given one (or at the left edge, if it started
     * earlier), so moving up and down keeps the time
     */
    private Slot slotInRow(int row, Slot from) {
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        Instant leftEdge = timeline.timeAt(scrollX());
        Instant time = from.start().isBefore(leftEdge) ? leftEdge : from.start();
        return GuideTimeline.slotAt(rows.get(row), time);
    }

    private void scrollIntoView(Block block) {
        Bounds viewport = scrollPane.getViewportBounds();
        double scrollableWidth = grid.getPrefWidth() - viewport.getWidth();
        double scrollableHeight = grid.getPrefHeight() - viewport.getHeight();
        double left = scrollX();
        double top = scrollY();
        double x = block.getLayoutX();
        double y = block.getLayoutY();
        if (scrollableWidth > 0 && (x < left || x > left + viewport.getWidth() - 80)) {
            scrollPane.setHvalue(Math.clamp((x - 60) / scrollableWidth, 0, 1));
        }
        if (scrollableHeight > 0) {
            if (y < top) {
                scrollPane.setVvalue(Math.clamp(y / scrollableHeight, 0, 1));
            } else if (y + ROW_HEIGHT > top + viewport.getHeight()) {
                scrollPane.setVvalue(Math.clamp((y + ROW_HEIGHT - viewport.getHeight()) / scrollableHeight, 0, 1));
            }
        }
    }
}

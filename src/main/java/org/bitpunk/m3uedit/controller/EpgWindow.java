package org.bitpunk.m3uedit.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.Programme;
import org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Shows the programmes of all channels of a playlist in a table, read from the programme guides of the enrichers.
 * The programmes are read in the background when the window is opened.
 */
@Slf4j
public class EpgWindow extends Stage {
    static final String ALL_CHANNELS = "All channels";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * A choice of the day filter.
     *
     * @param label shown to the user
     * @param date  the day shown, null for {@link #FROM_NOW} and {@link #ALL}
     */
    record DayFilter(String label, LocalDate date) {
        static final DayFilter FROM_NOW = new DayFilter("From now", null);
        static final DayFilter ALL = new DayFilter("All days", null);

        @Override
        public String toString() {
            return label;
        }
    }

    private final List<Entry> playlist;
    private final List<ProgrammeGuideProvider> providers;
    private final Clock clock;
    private final ZoneId zone;
    @Getter
    private final ObservableList<Programme> programmes = FXCollections.observableArrayList();
    private final FilteredList<Programme> filteredProgrammes = new FilteredList<>(programmes);
    @Getter
    private final TableView<Programme> table = new TableView<>();
    @Getter
    private final ComboBox<String> channelComboBox = new ComboBox<>();
    @Getter
    private final ComboBox<DayFilter> dayComboBox = new ComboBox<>();
    @Getter
    private final TextField searchField = new TextField();
    @Getter
    private final TextArea descriptionArea = new TextArea();
    @Getter
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();

    /**
     * @param playlist  the playlist, a copy is taken
     * @param providers the enrichers providing the programmes
     */
    public EpgWindow(Window owner, String projectName, List<Entry> playlist, List<ProgrammeGuideProvider> providers) {
        this(owner, projectName, playlist, providers, Clock.systemDefaultZone());
    }

    EpgWindow(Window owner, String projectName, List<Entry> playlist, List<ProgrammeGuideProvider> providers, Clock clock) {
        this.playlist = List.copyOf(playlist);
        this.providers = List.copyOf(providers);
        this.clock = clock;
        this.zone = clock.getZone();
        if (owner != null) {
            initOwner(owner);
        }
        setTitle("EPG – " + projectName);

        channelComboBox.getItems().setAll(ALL_CHANNELS);
        channelComboBox.getSelectionModel().selectFirst();
        dayComboBox.getItems().setAll(DayFilter.FROM_NOW, DayFilter.ALL);
        dayComboBox.getSelectionModel().selectFirst();
        searchField.setPromptText("Search title or description");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        channelComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        dayComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        progress.setPrefSize(16, 16);
        progress.setMaxSize(16, 16);
        HBox toolbar = new HBox(8, new Label("Channel:"), channelComboBox, new Label("Day:"), dayComboBox, searchField,
                progress);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));

        initTable();
        descriptionArea.setEditable(false);
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(4);
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                descriptionArea.setText(describe(selected)));
        SplitPane splitPane = new SplitPane(table, descriptionArea);
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setDividerPositions(0.8);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        statusLabel.setPadding(new Insets(4, 8, 6, 8));
        statusLabel.setText("Reading the programme guides …");

        VBox root = new VBox(toolbar, splitPane, statusLabel);
        setScene(new Scene(root, 1100, 700));
    }

    private void initTable() {
        TableColumn<Programme, String> channel = column("Channel", Programme::channelName, 160);
        TableColumn<Programme, Instant> day = timeColumn("Day", Programme::start, DAY, 90);
        TableColumn<Programme, Instant> start = timeColumn("Start", Programme::start, TIME, 60);
        TableColumn<Programme, Instant> end = timeColumn("End", Programme::stop, TIME, 60);
        TableColumn<Programme, String> title = column("Title", programme -> programme.subTitle() == null
                ? programme.title() : "%s – %s".formatted(programme.title(), programme.subTitle()), 420);
        TableColumn<Programme, String> category = column("Category", Programme::category, 140);
        table.getColumns().setAll(List.of(channel, day, start, end, title, category));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No programmes."));
        SortedList<Programme> sorted = new SortedList<>(filteredProgrammes);
        // unsorted by the user: by start, then in the order of the playlist
        sorted.comparatorProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                () -> table.getComparator() != null ? table.getComparator() : defaultOrder(), table.comparatorProperty()));
        table.setItems(sorted);
    }

    private Comparator<Programme> defaultOrder() {
        List<String> channelOrder = playlist.stream().map(Entry::getTvgId).filter(Objects::nonNull).map(String::strip)
                .distinct().toList();
        return Comparator.comparing(Programme::start, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(programme -> channelOrder.indexOf(programme.channelId()));
    }

    private static TableColumn<Programme, String> column(String title, Function<Programme, String> value, double width) {
        TableColumn<Programme, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private TableColumn<Programme, Instant> timeColumn(String title, Function<Programme, Instant> value,
                                                       DateTimeFormatter format, double width) {
        TableColumn<Programme, Instant> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Instant instant, boolean empty) {
                super.updateItem(instant, empty);
                setText(empty || instant == null ? null : format.format(instant.atZone(zone)));
            }
        });
        column.setPrefWidth(width);
        column.setMinWidth(Region.USE_PREF_SIZE);
        return column;
    }

    /**
     * Reads the programmes in the background.
     */
    public void load() {
        load(runnable -> {
            Thread thread = new Thread(runnable, "epg-window");
            thread.setDaemon(true);
            thread.start();
        });
    }

    void load(Executor executor) {
        Task<List<Programme>> task = new Task<>() {
            @Override
            protected List<Programme> call() throws Exception {
                List<Programme> read = new ArrayList<>();
                for (ProgrammeGuideProvider provider : providers) {
                    read.addAll(provider.readProgrammes(playlist));
                }
                return read;
            }
        };
        task.setOnSucceeded(event -> showProgrammes(task.getValue()));
        task.setOnFailed(event -> {
            log.error("The programmes could not be read", task.getException());
            progress.setVisible(false);
            statusLabel.setText("The programme guides could not be read: " + task.getException().getMessage());
        });
        executor.execute(task);
    }

    void showProgrammes(List<Programme> read) {
        progress.setVisible(false);
        programmes.setAll(read);
        Set<String> channels = new LinkedHashSet<>();
        channels.add(ALL_CHANNELS);
        // in the order of the playlist
        for (Entry entry : playlist) {
            read.stream().filter(programme -> Objects.equals(programme.channelId(), strip(entry.getTvgId())))
                    .findFirst().ifPresent(programme -> channels.add(programme.channelName()));
        }
        channelComboBox.getItems().setAll(channels);
        channelComboBox.getSelectionModel().selectFirst();
        List<DayFilter> days = new ArrayList<>(List.of(DayFilter.FROM_NOW, DayFilter.ALL));
        read.stream().map(Programme::start).filter(Objects::nonNull).map(start -> start.atZone(zone).toLocalDate())
                .distinct().sorted()
                .forEach(date -> days.add(new DayFilter(DAY.format(date), date)));
        dayComboBox.getItems().setAll(days);
        dayComboBox.getSelectionModel().selectFirst();
        updateFilter();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private void updateFilter() {
        filteredProgrammes.setPredicate(filter(channelComboBox.getValue(), dayComboBox.getValue(), searchField.getText(),
                clock.instant(), zone));
        long channelCount = programmes.stream().map(Programme::channelId).distinct().count();
        statusLabel.setText("%d of %d programmes of %d channels".formatted(filteredProgrammes.size(), programmes.size(),
                channelCount));
    }

    /**
     * @return the filter of the programmes shown
     */
    static Predicate<Programme> filter(String channel, DayFilter day, String search, Instant now, ZoneId zone) {
        String text = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return programme -> {
            if (channel != null && !ALL_CHANNELS.equals(channel) && !channel.equals(programme.channelName())) {
                return false;
            }
            if (day == DayFilter.FROM_NOW) {
                Instant end = programme.stop() != null ? programme.stop() : programme.start();
                if (end != null && !end.isAfter(now)) {
                    return false;
                }
            } else if (day != null && day.date() != null && (programme.start() == null
                    || !programme.start().atZone(zone).toLocalDate().equals(day.date()))) {
                return false;
            }
            return text.isEmpty() || contains(programme.title(), text) || contains(programme.subTitle(), text)
                    || contains(programme.description(), text);
        };
    }

    private static boolean contains(String value, String text) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(text);
    }

    private String describe(Programme programme) {
        if (programme == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(programme.title() == null ? "" : programme.title());
        if (programme.subTitle() != null) {
            text.append(" – ").append(programme.subTitle());
        }
        text.append("\n").append(programme.channelName());
        if (programme.start() != null) {
            text.append(", ").append(DAY.format(programme.start().atZone(zone))).append(' ')
                    .append(TIME.format(programme.start().atZone(zone)));
            if (programme.stop() != null) {
                text.append(" – ").append(TIME.format(programme.stop().atZone(zone)));
            }
        }
        if (programme.category() != null) {
            text.append(" (").append(programme.category()).append(')');
        }
        if (programme.description() != null) {
            text.append("\n\n").append(programme.description());
        }
        return text.toString();
    }
}

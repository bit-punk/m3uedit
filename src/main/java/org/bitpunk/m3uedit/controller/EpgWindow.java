package org.bitpunk.m3uedit.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.Programme;
import org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider;
import org.bitpunk.m3uedit.util.ListControllerUtil;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;

/**
 * Shows the programmes of all channels of a playlist as programme guide (see {@link ProgrammeGuideView}), read from
 * the programme guides of the enrichers in the background when the window is opened. The selected programme is
 * described below the guide.
 */
@Slf4j
public class EpgWindow extends Stage {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * A day to jump to.
     */
    record Day(LocalDate date, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * A part of the day to jump to.
     */
    enum PartOfDay {
        MORNING("Morning", LocalTime.of(6, 0)),
        AFTERNOON("Afternoon", LocalTime.of(12, 0)),
        EVENING("Evening", LocalTime.of(18, 0)),
        NIGHT("Night", LocalTime.of(22, 0));

        final String label;
        final LocalTime start;

        PartOfDay(String label, LocalTime start) {
            this.label = label;
            this.start = start;
        }

        static PartOfDay of(LocalTime time) {
            PartOfDay part = NIGHT;
            for (PartOfDay candidate : values()) {
                if (!time.isBefore(candidate.start)) {
                    part = candidate;
                }
            }
            // after midnight it is still night
            return time.isBefore(MORNING.start) ? NIGHT : part;
        }

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
    private final ProgrammeGuideView guide;
    @Getter
    private final ComboBox<Day> dayComboBox = new ComboBox<>();
    @Getter
    private final ComboBox<PartOfDay> partOfDayComboBox = new ComboBox<>();
    @Getter
    private final Button nowButton = new Button("Now");
    @Getter
    private final TextField searchField = new TextField();
    @Getter
    private final Label titleLabel = new Label();
    @Getter
    private final Label timeLabel = new Label();
    @Getter
    private final Label descriptionLabel = new Label();
    private final ImageView imageView = new ImageView();
    @Getter
    private final Label statusLabel = new Label();
    private final Label loadingLabel = new Label("Reading the programme guides …");
    @Getter
    private List<Programme> programmes = List.of();
    /**
     * Set while the choices are updated to the time shown, so they do not scroll.
     */
    private boolean updatingChoices;
    private final Timeline clockTimer;

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
        this.guide = new ProgrammeGuideView(zone);
        guide.setOnPlay(ListControllerUtil::playEntry);
        if (owner != null) {
            initOwner(owner);
        }
        setTitle("EPG – " + projectName);

        partOfDayComboBox.getItems().setAll(PartOfDay.values());
        dayComboBox.valueProperty().addListener((observable, oldDay, day) -> jumpToChoice());
        partOfDayComboBox.valueProperty().addListener((observable, oldPart, part) -> jumpToChoice());
        nowButton.setOnAction(event -> showNow());
        searchField.setPromptText("Search title or description");
        searchField.textProperty().addListener((observable, oldText, text) -> guide.setSearch(text));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, dayComboBox, partOfDayComboBox, nowButton, spacer, searchField);
        toolbar.getStyleClass().add("guide-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 12, 10, 12));

        loadingLabel.getStyleClass().add("guide-loading");
        loadingLabel.setWrapText(true);
        StackPane guideArea = new StackPane(guide, loadingLabel);
        VBox.setVgrow(guideArea, Priority.ALWAYS);

        VBox details = details();
        guide.getSelectedProgramme().addListener((observable, oldProgramme, programme) -> describe(programme));
        // the time shown is reflected by the choices
        guide.getScrollPane().hvalueProperty().addListener((observable, oldValue, newValue) -> updateChoices());

        statusLabel.getStyleClass().add("guide-status");
        statusLabel.setPadding(new Insets(4, 12, 6, 12));
        VBox root = new VBox(toolbar, guideArea, details, statusLabel);
        root.getStyleClass().add("guide-window");
        Scene scene = new Scene(root, 1200, 720);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/css/epg.css"))
                .toExternalForm());
        setScene(scene);

        clockTimer = new Timeline(new KeyFrame(Duration.seconds(30), event -> guide.setNow(clock.instant())));
        clockTimer.setCycleCount(Timeline.INDEFINITE);
        setOnShown(event -> clockTimer.play());
        setOnHidden(event -> clockTimer.stop());
    }

    private VBox details() {
        imageView.setFitWidth(176);
        imageView.setFitHeight(99);
        imageView.setPreserveRatio(true);
        StackPane image = new StackPane(imageView);
        image.getStyleClass().add("guide-image");
        image.setMinSize(176, 99);
        image.setMaxSize(176, 99);
        titleLabel.getStyleClass().add("guide-details-title");
        timeLabel.getStyleClass().add("guide-details-time");
        descriptionLabel.getStyleClass().add("guide-details-description");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setAlignment(Pos.TOP_LEFT);
        descriptionLabel.setMaxHeight(Double.MAX_VALUE);
        VBox text = new VBox(4, titleLabel, timeLabel, descriptionLabel);
        VBox.setVgrow(descriptionLabel, Priority.ALWAYS);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox box = new HBox(16, image, text);
        box.setPadding(new Insets(14, 16, 10, 16));
        VBox details = new VBox(box);
        details.getStyleClass().add("guide-details");
        details.setMinHeight(150);
        details.setPrefHeight(150);
        return details;
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
            statusLabel.setText("The programme guides could not be read: " + task.getException().getMessage());
            loadingLabel.setText(statusLabel.getText());
        });
        statusLabel.setText("Reading the programme guides …");
        executor.execute(task);
    }

    /**
     * Shows the programmes, a row for each entry of the playlist in its order, also without programmes.
     */
    void showProgrammes(List<Programme> read) {
        programmes = List.copyOf(read);
        loadingLabel.setVisible(read.isEmpty());
        loadingLabel.setText("No programmes for the channels of the playlist.");
        Map<String, List<Programme>> byChannel = new HashMap<>();
        read.forEach(programme -> byChannel.computeIfAbsent(programme.channelId(), id -> new ArrayList<>()).add(programme));
        List<ProgrammeGuideView.Channel> channels = new ArrayList<>();
        List<List<Programme>> rows = new ArrayList<>();
        Set<String> shownIds = new HashSet<>();
        int channelsWithProgrammes = 0;
        for (int i = 0; i < playlist.size(); i++) {
            Entry entry = playlist.get(i);
            String id = entry.getTvgId() == null || entry.getTvgId().isBlank() ? null : entry.getTvgId().strip();
            List<Programme> row = id == null ? List.of() : byChannel.getOrDefault(id, List.of());
            if (id != null && !shownIds.add(id)) {
                // another entry of the same channel: its own programmes, each block is selected on its own
                row = row.stream().map(programme -> copy(programme, entry.getName())).toList();
            }
            if (!row.isEmpty()) {
                channelsWithProgrammes++;
            }
            // numbered like the rows of the playlist table
            channels.add(new ProgrammeGuideView.Channel(entry.getName(), entry.getTvgLogo(), entry, i + 1));
            rows.add(row);
        }
        Instant now = clock.instant();
        guide.setChannels(channels, rows, now);

        Set<LocalDate> dates = new TreeSet<>();
        guide.getRows().forEach(row -> row.forEach(slot -> {
            dates.add(slot.start().atZone(zone).toLocalDate());
        }));
        LocalDate today = now.atZone(zone).toLocalDate();
        dates.add(today);
        updatingChoices = true;
        dayComboBox.getItems().setAll(dates.stream().map(date -> new Day(date, label(date, today))).toList());
        updatingChoices = false;
        statusLabel.setText("%d programmes, %d of %d channels with programmes".formatted(read.size(),
                channelsWithProgrammes, channels.size()));
        // the viewport has its size after the next layout
        Platform.runLater(this::showNow);
    }

    private static Programme copy(Programme programme, String channelName) {
        return new Programme(programme.channelId(), channelName, programme.start(), programme.stop(), programme.title(),
                programme.subTitle(), programme.description(), programme.category(), programme.icon());
    }

    private static String label(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "Today";
        }
        if (date.equals(today.plusDays(1))) {
            return "Tomorrow";
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return DAY.format(date);
    }

    /**
     * Scrolls to the current time and selects the programme running on the first channel.
     */
    void showNow() {
        Instant now = clock.instant();
        guide.setNow(now);
        guide.scrollTo(now);
        guide.selectAt(now);
        updateChoices(now);
        guide.requestFocus();
    }

    private void jumpToChoice() {
        if (updatingChoices || dayComboBox.getValue() == null || partOfDayComboBox.getValue() == null) {
            return;
        }
        Instant target = dayComboBox.getValue().date().atTime(partOfDayComboBox.getValue().start).atZone(zone).toInstant();
        guide.scrollTo(target);
    }

    /**
     * Shows the day and part of the day at the left edge of the guide.
     */
    private void updateChoices() {
        if (guide.getTimeline() == null) {
            return;
        }
        // the left edge is 60 pixels before the time jumped to
        updateChoices(guide.getTimeline().timeAt(guide.scrollX() + 60));
    }

    private void updateChoices(Instant time) {
        LocalDate date = time.atZone(zone).toLocalDate();
        LocalTime localTime = time.atZone(zone).toLocalTime();
        // the night of a day reaches until the morning of the next one
        if (localTime.isBefore(PartOfDay.MORNING.start)) {
            date = date.minusDays(1);
        }
        LocalDate shownDate = date;
        updatingChoices = true;
        try {
            dayComboBox.getItems().stream().filter(day -> day.date().equals(shownDate)).findFirst()
                    .ifPresent(dayComboBox::setValue);
            partOfDayComboBox.setValue(PartOfDay.of(localTime));
        } finally {
            updatingChoices = false;
        }
    }

    private void describe(Programme programme) {
        if (programme == null) {
            titleLabel.setText(null);
            timeLabel.setText(null);
            descriptionLabel.setText(null);
            imageView.setImage(null);
            return;
        }
        titleLabel.setText(programme.subTitle() == null ? programme.title()
                : "%s – %s".formatted(programme.title(), programme.subTitle()));
        StringBuilder time = new StringBuilder(programme.channelName());
        if (programme.start() != null) {
            time.append(" · ").append(DAY.format(programme.start().atZone(zone))).append(' ')
                    .append(TIME.format(programme.start().atZone(zone)));
            if (programme.stop() != null) {
                time.append(" – ").append(TIME.format(programme.stop().atZone(zone)));
            }
        }
        if (programme.category() != null) {
            time.append(" · ").append(programme.category());
        }
        timeLabel.setText(time.toString());
        descriptionLabel.setText(programme.description());
        imageView.setImage(programme.icon() == null ? null : new Image(programme.icon(), 176, 99, true, true, true));
    }
}

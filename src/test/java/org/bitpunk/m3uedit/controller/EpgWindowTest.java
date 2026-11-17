package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.bitpunk.m3uedit.controller.EpgWindow.PartOfDay;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.Programme;
import org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class EpgWindowTest {
    private static final Instant NOW = Instant.parse("2026-11-13T19:10:00Z");

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private static Programme programme(String channel, String start, String stop, String title) {
        return new Programme(channel + ".de", channel, Instant.parse(start), Instant.parse(stop), title, null,
                "about " + title, "Show");
    }

    private static final Programme ARD_NEWS = programme("ARD", "2026-11-13T19:00:00Z", "2026-11-13T19:15:00Z", "Tagesschau");
    private static final Programme ARD_CRIME = programme("ARD", "2026-11-13T19:15:00Z", "2026-11-13T20:45:00Z", "Tatort");
    private static final Programme ZDF_EARLY = programme("ZDF", "2026-11-13T18:00:00Z", "2026-11-13T19:00:00Z", "heute");
    private static final Programme ZDF_SHOW = programme("ZDF", "2026-11-13T19:00:00Z", "2026-11-13T20:00:00Z", "Show");
    private static final Programme ZDF_TOMORROW = programme("ZDF", "2026-11-14T08:00:00Z", "2026-11-14T09:00:00Z", "Morgen");

    private static Entry entry(String name, String tvgId) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setTvgId(tvgId);
        return entry;
    }

    /**
     * Loads the programmes and applies the action afterwards: a task reports its result on the FX thread later,
     * and the guide scrolls to the current time after the next layout.
     */
    private static <T> T onFxThread(List<ProgrammeGuideProvider> providers, Function<EpgWindow, T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            EpgWindow window = new EpgWindow(null, "test",
                    List.of(entry("ARD", "ARD.de"), entry("No EPG", null), entry("ZDF", "ZDF.de"), entry("ZDF again", "ZDF.de")),
                    providers, Clock.fixed(NOW, ZoneId.of("UTC")));
            window.load(Runnable::run);
            Platform.runLater(() -> Platform.runLater(() -> {
                try {
                    result.complete(action.apply(window));
                } catch (RuntimeException | AssertionError e) {
                    result.completeExceptionally(e);
                }
            }));
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static <T> T onFxThread(Function<EpgWindow, T> action) throws Exception {
        return onFxThread(List.of(playlist -> List.of(ARD_NEWS, ARD_CRIME, ZDF_EARLY, ZDF_SHOW, ZDF_TOMORROW)), action);
    }

    private static void press(EpgWindow window, KeyCode code) {
        Event.fireEvent(window.getGuide(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    @Test
    void testEveryEntryIsShownInTheOrderOfThePlaylist() throws Exception {
        List<Object> state = onFxThread(window -> List.of(
                window.getGuide().getChannels().stream().map(ProgrammeGuideView.Channel::name).toList(),
                window.getGuide().getRows().stream().map(row -> row.stream().map(slot -> slot.programme().title()).toList()).toList(),
                window.getDayComboBox().getItems().stream().map(EpgWindow.Day::label).toList(),
                window.getStatusLabel().getText()));

        assertThat(state.get(0)).isEqualTo(List.of("ARD", "No EPG", "ZDF", "ZDF again"));
        // an entry without programmes has an empty row, another entry of the same channel its programmes
        assertThat(state.get(1)).isEqualTo(List.of(List.of("Tagesschau", "Tatort"), List.of(),
                List.of("heute", "Show", "Morgen"), List.of("heute", "Show", "Morgen")));
        assertThat(state.get(2)).isEqualTo(List.of("Today", "Tomorrow"));
        assertThat(state.get(3)).isEqualTo("5 programmes, 3 of 4 channels with programmes");
    }

    @Test
    void testChannelsAreNumberedLikeTheRowsOfThePlaylist() throws Exception {
        List<Integer> numbers = onFxThread(window -> window.getGuide().getChannels().stream()
                .map(ProgrammeGuideView.Channel::number).toList());

        assertThat(numbers).containsExactly(1, 2, 3, 4);
    }

    @Test
    void testTheProgrammeRunningNowIsSelectedAndDescribed() throws Exception {
        List<Object> state = onFxThread(window -> List.of(
                window.getGuide().getSelectedProgramme().get(),
                window.getTitleLabel().getText(),
                window.getTimeLabel().getText(),
                window.getDescriptionLabel().getText(),
                window.getDayComboBox().getValue().label(),
                window.getPartOfDayComboBox().getValue()));

        assertThat(state.get(0)).isEqualTo(ARD_NEWS);
        assertThat(state.get(1)).isEqualTo("Tagesschau");
        assertThat((String) state.get(2)).startsWith("ARD · ").endsWith("19:00 – 19:15 · Show");
        assertThat(state.get(3)).isEqualTo("about Tagesschau");
        assertThat(state.get(4)).isEqualTo("Today");
        assertThat(state.get(5)).isEqualTo(PartOfDay.EVENING);
    }

    @Test
    void testArrowKeysMoveTheSelection() throws Exception {
        List<Programme> selected = onFxThread(window -> {
            List<Programme> path = new java.util.ArrayList<>();
            for (KeyCode code : List.of(KeyCode.RIGHT, KeyCode.RIGHT, KeyCode.DOWN, KeyCode.LEFT, KeyCode.UP, KeyCode.UP)) {
                press(window, code);
                path.add(window.getGuide().getSelectedProgramme().get());
            }
            return path;
        });

        // the last programme of a row and the first row stop the selection, the row without programmes is skipped
        assertThat(selected).containsExactly(ARD_CRIME, ARD_CRIME, ZDF_SHOW, ZDF_EARLY, ARD_NEWS, ARD_NEWS);
    }

    @Test
    void testFailureIsShown() throws Exception {
        String status = onFxThread(List.of(playlist -> {
            throw new IOException("source down");
        }), window -> window.getStatusLabel().getText());

        assertThat(status).contains("could not be read").contains("source down");
    }

    @Test
    void testPartsOfTheDay() {
        assertThat(PartOfDay.of(LocalTime.of(3, 0))).isEqualTo(PartOfDay.NIGHT);
        assertThat(PartOfDay.of(LocalTime.of(6, 0))).isEqualTo(PartOfDay.MORNING);
        assertThat(PartOfDay.of(LocalTime.of(13, 0))).isEqualTo(PartOfDay.AFTERNOON);
        assertThat(PartOfDay.of(LocalTime.of(19, 0))).isEqualTo(PartOfDay.EVENING);
        assertThat(PartOfDay.of(LocalTime.of(23, 0))).isEqualTo(PartOfDay.NIGHT);
    }

    private static void click(javafx.scene.Node node, int clickCount) {
        Event.fireEvent(node, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1,
                javafx.scene.input.MouseButton.PRIMARY, clickCount, false, false, false, false, true, false, false,
                true, false, true, null));
    }

    @Test
    void testChannelsArePlayedByClickingTheirNameDoubleClickingAProgrammeOrPressingEnter() throws Exception {
        List<String> played = onFxThread(window -> {
            List<String> names = new java.util.ArrayList<>();
            window.getGuide().setOnPlay(entry -> names.add(entry.getName()));
            List<javafx.scene.Node> channels = List.copyOf(window.getGuide().lookupAll(".guide-channel"));
            click(channels.get(1), 1);
            javafx.scene.Node news = window.getGuide().blockOf(ARD_NEWS);
            // a single click only selects
            click(news, 1);
            click(news, 2);
            press(window, KeyCode.DOWN);
            press(window, KeyCode.ENTER);
            return names;
        });

        // a channel without programmes is played too; DOWN skips its empty row
        assertThat(played).containsExactly("No EPG", "ARD", "ZDF");
    }

    @Test
    void testTheScrollPaneDoesNotKeepTheFocus() throws Exception {
        // clicking a programme focuses the scroll pane first; its focus ring shifted the guide for a moment
        Object focusOwner = onFxThread(window -> {
            window.getGuide().getScrollPane().requestFocus();
            return window.getScene().getFocusOwner();
        });

        assertThat(focusOwner).isInstanceOf(ProgrammeGuideView.class);
    }

    /**
     * Runs the action on the JavaFX thread after the pending events (results of tasks, layouts) have been processed.
     */
    private static <T> T later(java.util.concurrent.Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> Platform.runLater(() -> Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Exception | AssertionError e) {
                result.completeExceptionally(e);
            }
        })));
        return result.get(10, TimeUnit.SECONDS);
    }

    private static List<String> channelNames(EpgWindow window) {
        return window.getGuide().getChannels().stream().map(ProgrammeGuideView.Channel::name).toList();
    }

    @Test
    void testARefreshShowsTheNewPlaylistAndKeepsTheSelectionAndTheSearch() throws Exception {
        List<ProgrammeGuideProvider> providers = List.of(playlist -> List.of(ARD_NEWS, ARD_CRIME, ZDF_EARLY, ZDF_SHOW, ZDF_TOMORROW));
        EpgWindow window = onFxThread(providers, opened -> {
            opened.getGuide().getSelectedProgramme().set(ZDF_SHOW);
            opened.getSearchField().setText("Tatort");
            return opened;
        });

        later(() -> {
            // e.g. a sorter moved ZDF to the top and a filter removed the entry without EPG
            window.refresh(List.of(entry("ZDF", "ZDF.de"), entry("ARD", "ARD.de")), providers, Runnable::run);
            return null;
        });
        List<Object> state = later(() -> List.of(
                channelNames(window),
                window.getGuide().getSelectedProgramme().get(),
                window.getGuide().blockOf(ARD_NEWS).getPseudoClassStates().stream().map(Object::toString).toList(),
                window.getStatusLabel().getText()));

        assertThat(state.get(0)).isEqualTo(List.of("ZDF", "ARD"));
        assertThat(state.get(1)).isEqualTo(ZDF_SHOW);
        assertThat((List<?>) state.get(2)).asString().contains("dimmed");
        assertThat(state.get(3)).isEqualTo("5 programmes, 2 of 2 channels with programmes");
    }

    @Test
    void testARefreshWithoutProvidersShowsAHint() throws Exception {
        EpgWindow window = onFxThread(opened -> opened);

        String status = later(() -> {
            window.refresh(List.of(entry("ARD", "ARD.de")), List.of(), Runnable::run);
            return window.getStatusLabel().getText();
        });

        assertThat(status).contains("Enable the EPG Enricher");
        assertThat(later(() -> window.getGuide().getRows())).singleElement().asList().isEmpty();
    }

    @Test
    void testTheResultOfAnOutdatedRefreshIsDiscarded() throws Exception {
        EpgWindow window = onFxThread(opened -> opened);
        List<Runnable> slowLoad = new java.util.ArrayList<>();

        later(() -> {
            window.refresh(List.of(entry("Old", "ARD.de")), List.of(playlist -> List.of(ARD_NEWS)), slowLoad::add);
            window.refresh(List.of(entry("New", "ZDF.de")), List.of(playlist -> List.of(ZDF_SHOW)), Runnable::run);
            return null;
        });
        // the older load finishes last
        later(() -> {
            slowLoad.getFirst().run();
            return null;
        });

        assertThat(later(() -> channelNames(window))).containsExactly("New");
    }
}

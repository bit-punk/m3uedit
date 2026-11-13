package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import org.bitpunk.m3uedit.controller.EpgWindow.DayFilter;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.Programme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class EpgWindowTest {
    private static final Instant NOW = Instant.parse("2026-11-13T20:00:00Z");

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private static Programme programme(String channel, String start, String stop, String title, String description) {
        return new Programme(channel + ".de", channel, Instant.parse(start), stop == null ? null : Instant.parse(stop),
                title, null, description, null);
    }

    private static final Programme ENDED = programme("ZDF", "2026-11-13T18:00:00Z", "2026-11-13T19:00:00Z", "heute", null);
    private static final Programme RUNNING = programme("ZDF", "2026-11-13T19:30:00Z", "2026-11-13T21:00:00Z", "Krimi", "Mord im Dorf");
    private static final Programme TOMORROW = programme("ARD", "2026-11-14T10:00:00Z", null, "Sport", "Fußball live");

    private static List<Programme> filtered(String channel, DayFilter day, String search) {
        Predicate<Programme> filter = EpgWindow.filter(channel, day, search, NOW, ZoneOffset.UTC);
        return List.of(ENDED, RUNNING, TOMORROW).stream().filter(filter).toList();
    }

    @Test
    void testProgrammesAreFilteredByChannelDayAndText() {
        assertThat(filtered(EpgWindow.ALL_CHANNELS, DayFilter.FROM_NOW, "")).containsExactly(RUNNING, TOMORROW);
        assertThat(filtered(EpgWindow.ALL_CHANNELS, DayFilter.ALL, null)).containsExactly(ENDED, RUNNING, TOMORROW);
        assertThat(filtered("ZDF", DayFilter.ALL, "")).containsExactly(ENDED, RUNNING);
        assertThat(filtered(EpgWindow.ALL_CHANNELS, new DayFilter("Sat", LocalDate.of(2026, 11, 14)), "")).containsExactly(TOMORROW);
        assertThat(filtered(EpgWindow.ALL_CHANNELS, DayFilter.ALL, " FUSSBALL")).isEmpty();
        assertThat(filtered(EpgWindow.ALL_CHANNELS, DayFilter.ALL, "fußball")).containsExactly(TOMORROW);
        assertThat(filtered(EpgWindow.ALL_CHANNELS, DayFilter.ALL, "mord")).containsExactly(RUNNING);
    }

    private static EpgWindow window(List<org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider> providers) {
        Entry ard = new Entry();
        ard.setName("ARD");
        ard.setTvgId("ARD.de");
        Entry zdf = new Entry();
        zdf.setName("ZDF");
        zdf.setTvgId("ZDF.de");
        return new EpgWindow(null, "test", List.of(ard, zdf), providers, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    /**
     * Loads the programmes and applies the action afterwards: a task reports its result on the FX thread later.
     */
    private static <T> T onFxThread(List<org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider> providers,
                                    Function<EpgWindow, T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            EpgWindow window = window(providers);
            window.load(Runnable::run);
            Platform.runLater(() -> {
                try {
                    result.complete(action.apply(window));
                } catch (RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static <T> T onFxThread(Function<EpgWindow, T> action) throws Exception {
        return onFxThread(List.of(playlist -> List.of(ENDED, RUNNING, TOMORROW)), action);
    }

    @Test
    void testProgrammesFromNowAreShownWithChannelsInTheOrderOfThePlaylist() throws Exception {
        List<Object> state = onFxThread(window -> List.of(
                List.copyOf(window.getTable().getItems()),
                List.copyOf(window.getChannelComboBox().getItems()),
                window.getDayComboBox().getItems().stream().map(day -> day.date() == null ? day.label() : day.date()).toList(),
                window.getStatusLabel().getText()));

        assertThat(state.get(0)).isEqualTo(List.of(RUNNING, TOMORROW));
        assertThat(state.get(1)).isEqualTo(List.of(EpgWindow.ALL_CHANNELS, "ARD", "ZDF"));
        assertThat(state.get(2)).isEqualTo(List.of("From now", "All days", LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 14)));
        assertThat(state.get(3)).isEqualTo("2 of 3 programmes of 2 channels");
    }

    @Test
    void testSelectedProgrammeIsDescribed() throws Exception {
        String description = onFxThread(window -> {
            window.getTable().getSelectionModel().select(RUNNING);
            return window.getDescriptionArea().getText();
        });

        assertThat(description).startsWith("Krimi\nZDF, ").endsWith("Mord im Dorf");
    }

    @Test
    void testFailureIsShown() throws Exception {
        String status = onFxThread(List.of(playlist -> {
            throw new IOException("source down");
        }), window -> window.getStatusLabel().getText());

        assertThat(status).contains("could not be read").contains("source down");
    }
}

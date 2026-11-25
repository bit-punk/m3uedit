package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.api.Programme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Scrolling over the channel column and the time axis, which are not part of the scroll pane of the programmes.
 */
class ProgrammeGuideViewScrollTest {
    private static final Instant NOW = Instant.parse("2026-11-13T19:10:00Z");

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
        Platform.setImplicitExit(false);
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    /**
     * 30 channels with programmes over 12 hours: more than fit into the window in both directions.
     */
    private static ProgrammeGuideView guideShown(Stage stage) {
        ProgrammeGuideView guide = new ProgrammeGuideView(ZoneId.of("UTC"));
        List<ProgrammeGuideView.Channel> channels = new ArrayList<>();
        List<List<Programme>> programmes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            channels.add(new ProgrammeGuideView.Channel("Channel " + i, null));
            programmes.add(List.of(new Programme("c" + i, "Channel " + i, NOW.minusSeconds(3600),
                    NOW.plusSeconds(11 * 3600), "Programme " + i, null, null, null)));
        }
        guide.setChannels(channels, programmes, NOW);
        stage.setScene(new Scene(guide, 600, 300));
        stage.show();
        guide.applyCss();
        guide.layout();
        return guide;
    }

    private static ScrollEvent scroll(double deltaX, double deltaY, boolean shift) {
        return new ScrollEvent(ScrollEvent.SCROLL, 50, 50, 50, 50, shift, false, false, false, false, false,
                deltaX, deltaY, deltaX, deltaY, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
    }

    private static Node channelArea(ProgrammeGuideView guide) {
        return guide.lookup(".guide-channels").getParent();
    }

    private static Node timeArea(ProgrammeGuideView guide) {
        return guide.lookup(".guide-time-header").getParent();
    }

    @Test
    void theMouseWheelOverTheChannelsScrollsVertically() throws Exception {
        List<Double> state = onFxThread(() -> {
            Stage stage = new Stage();
            ProgrammeGuideView guide = guideShown(stage);
            ScrollPane scrollPane = guide.getScrollPane();

            // the mouse wheel turned towards the user: the channels below are shown
            Event.fireEvent(channelArea(guide), scroll(0, -100, false));
            double down = guide.scrollY();
            double column = guide.lookup(".guide-channels").getTranslateY();
            Event.fireEvent(channelArea(guide), scroll(0, 40, false));
            double up = guide.scrollY();
            double hvalue = scrollPane.getHvalue();
            stage.close();
            return List.of(down, column, up, hvalue);
        });

        assertThat(state.get(0)).isCloseTo(100, within(0.5));
        // the channels follow the programmes
        assertThat(state.get(1)).isCloseTo(-100, within(0.5));
        assertThat(state.get(2)).isCloseTo(60, within(0.5));
        assertThat(state.get(3)).as("not scrolled horizontally").isZero();
    }

    @Test
    void scrollingOverTheTimeAxisMovesInTime() throws Exception {
        List<Double> state = onFxThread(() -> {
            Stage stage = new Stage();
            ProgrammeGuideView guide = guideShown(stage);

            // the mouse wheel, Shift + mouse wheel and a touchpad scroll horizontally over the time axis
            Event.fireEvent(timeArea(guide), scroll(0, -100, false));
            double wheel = guide.scrollX();
            Event.fireEvent(timeArea(guide), scroll(0, -50, true));
            double shiftWheel = guide.scrollX();
            Event.fireEvent(timeArea(guide), scroll(-30, 0, false));
            double touchpad = guide.scrollX();
            double vertical = guide.scrollY();
            stage.close();
            return List.of(wheel, shiftWheel, touchpad, vertical);
        });

        assertThat(state.get(0)).isCloseTo(100, within(0.5));
        assertThat(state.get(1)).isCloseTo(150, within(0.5));
        assertThat(state.get(2)).isCloseTo(180, within(0.5));
        assertThat(state.get(3)).as("not scrolled vertically").isZero();
    }

    @Test
    void shiftAndTheMouseWheelOverTheChannelsScrollHorizontally() throws Exception {
        List<Double> state = onFxThread(() -> {
            Stage stage = new Stage();
            ProgrammeGuideView guide = guideShown(stage);

            Event.fireEvent(channelArea(guide), scroll(0, -80, true));
            List<Double> result = List.of(guide.scrollX(), guide.scrollY());
            stage.close();
            return result;
        });

        assertThat(state.get(0)).isCloseTo(80, within(0.5));
        assertThat(state.get(1)).isZero();
    }

    @Test
    void scrollingStopsAtTheEnd() throws Exception {
        List<Double> values = onFxThread(() -> {
            Stage stage = new Stage();
            ProgrammeGuideView guide = guideShown(stage);

            Event.fireEvent(channelArea(guide), scroll(0, 10_000, false));
            double top = guide.getScrollPane().getVvalue();
            Event.fireEvent(channelArea(guide), scroll(0, -100_000, false));
            double bottom = guide.getScrollPane().getVvalue();
            stage.close();
            return List.of(top, bottom);
        });

        assertThat(values).containsExactly(0.0, 1.0);
    }
}

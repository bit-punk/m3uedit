package org.bitpunk.m3uedit.plugins.input.iptvorg;

import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class IptvOrgInputDialogControllerTest {
    private static final IptvOrgPlaylist GERMANY = new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany");
    private static final IptvOrgPlaylist AUSTRIA = new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "AT", "Austria");
    private static final IptvOrgPlaylist NEWS = new IptvOrgPlaylist(PlaylistGrouping.CATEGORY, "news", "News");

    private static final IptvOrgApiClient STUB_CLIENT = new IptvOrgApiClient("http://localhost/", "http://localhost/") {
        @Override
        public synchronized List<IptvOrgPlaylist> getPlaylists(PlaylistGrouping grouping) {
            return switch (grouping) {
                case COUNTRY -> List.of(AUSTRIA, GERMANY);
                case CATEGORY -> List.of(NEWS);
                default -> List.of();
            };
        }
    };

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @Test
    void testDialogInitializationLoadsCountries() throws Exception {
        IptvOrgInputDialogController controller = callOnFxThread(() -> new IptvOrgInputDialogController(STUB_CLIENT, null));

        assertThat(controller.getTitle()).isEqualTo("Select iptv-org playlist(s) or channel(s) to read entries from");
        ComboBox<PlaylistGrouping> groupingComboBox = getField(controller, "groupingComboBox");
        assertThat(groupingComboBox.getValue()).isEqualTo(PlaylistGrouping.COUNTRY);
        ListView<IptvOrgPlaylist> available = getField(controller, "availablePlaylistListView");
        waitUntil(() -> available.getItems().size() == 2);
        assertThat(available.getItems()).containsExactly(AUSTRIA, GERMANY);
        assertThat(controller.getNothingSelected().get()).isTrue();
    }

    @Test
    void testPreselectedPlaylistsAreShownAndReturnedOnOk() throws Exception {
        IptvOrgInputDialogController controller = callOnFxThread(() -> new IptvOrgInputDialogController(STUB_CLIENT, List.of(NEWS)));

        assertThat(controller.getNothingSelected().get()).isFalse();
        List<IptvOrgPlaylist> result = callOnFxThread(() -> controller.getResultConverter().call(new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE)));
        assertThat(result).containsExactly(NEWS);
        List<IptvOrgPlaylist> cancelResult = callOnFxThread(() -> controller.getResultConverter().call(ButtonType.CANCEL));
        assertThat(cancelResult).isNull();
    }

    @Test
    void testFilterAndAddSelectedPlaylists() throws Exception {
        IptvOrgInputDialogController controller = callOnFxThread(() -> new IptvOrgInputDialogController(STUB_CLIENT, null));
        ListView<IptvOrgPlaylist> available = getField(controller, "availablePlaylistListView");
        ListView<IptvOrgPlaylist> selected = getField(controller, "selectedPlaylistListView");
        TextField filterTextField = getField(controller, "filterTextField");
        waitUntil(() -> available.getItems().size() == 2);

        callOnFxThread(() -> {
            filterTextField.setText("germ");
            available.getSelectionModel().selectAll();
            controller.addSelectedPlaylists();
            controller.addSelectedPlaylists();
            return null;
        });

        assertThat(available.getItems()).containsExactly(GERMANY);
        assertThat(selected.getItems()).containsExactly(GERMANY);
        assertThat(controller.getNothingSelected().get()).isFalse();
    }

    @Test
    void testMatchesFilter() {
        assertThat(IptvOrgInputDialogController.matchesFilter(GERMANY, null)).isTrue();
        assertThat(IptvOrgInputDialogController.matchesFilter(GERMANY, " ")).isTrue();
        assertThat(IptvOrgInputDialogController.matchesFilter(GERMANY, "GER")).isTrue();
        assertThat(IptvOrgInputDialogController.matchesFilter(GERMANY, "de")).isTrue();
        assertThat(IptvOrgInputDialogController.matchesFilter(GERMANY, "news")).isFalse();
    }

    private static <T> T callOnFxThread(Supplier<T> action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    private static void waitUntil(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!Boolean.TRUE.equals(callOnFxThread(condition))) {
            assertThat(System.currentTimeMillis()).as("condition not met within timeout").isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}

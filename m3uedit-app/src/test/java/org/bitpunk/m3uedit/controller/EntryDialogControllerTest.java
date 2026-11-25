package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.bitpunk.m3uedit.api.Entry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EntryDialogControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
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

    private static TextField field(EntryDialogController<Entry> dialog, String id) {
        return (TextField) dialog.getDialogPane().lookup("#" + id);
    }

    private static Node okButton(EntryDialogController<Entry> dialog) {
        ButtonType ok = dialog.getDialogPane().getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
        return dialog.getDialogPane().lookupButton(ok);
    }

    private static boolean markedInvalid(TextField field) {
        return field.getStyle() != null && !field.getStyle().isEmpty();
    }

    private static Entry entry() {
        Entry entry = new Entry();
        entry.setIndex(3);
        entry.setDuration("-1");
        entry.setName("Das Erste HD");
        entry.setChannelUri("http://example.com/daserste");
        return entry;
    }

    @Test
    void testANewEntryCanBeAddedWithoutPosition() throws Exception {
        List<Boolean> disabled = onFxThread(() -> {
            EntryDialogController<Entry> dialog = new EntryDialogController<>();
            boolean empty = okButton(dialog).isDisabled();
            field(dialog, "nameTextField").setText("ZDF");
            field(dialog, "channelUriTextField").setText("http://example.com/zdf");
            return List.of(empty, okButton(dialog).isDisabled(), field(dialog, "indexTextField").isVisible());
        });

        assertThat(disabled.get(0)).as("without name and URL").isTrue();
        // the hidden position (0) of a new entry must not be validated
        assertThat(disabled.get(1)).as("with name and URL").isFalse();
    }

    @Test
    void testAValidPositionIsNotMarkedAsInvalid() throws Exception {
        List<Boolean> state = onFxThread(() -> {
            EntryDialogController<Entry> dialog = new EntryDialogController<>(entry(), 120);
            TextField index = field(dialog, "indexTextField");
            boolean valid = !markedInvalid(index) && !okButton(dialog).isDisabled();
            index.setText("0");
            boolean invalid = markedInvalid(index) && okButton(dialog).isDisabled();
            index.setText("5");
            return List.of(valid, invalid, !markedInvalid(index) && !okButton(dialog).isDisabled());
        });

        assertThat(state).containsExactly(true, true, true);
    }

    @Test
    void testTheVlcOptionsKeepTheirOrder() throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("network-caching", "1000");
        options.put("http-user-agent", "m3uedit");
        options.put("adaptive-logic", "highest");
        Entry entry = entry();
        entry.setVlcOpts(options);

        Map<String, String> edited = onFxThread(() -> {
            EntryDialogController<Entry> dialog = new EntryDialogController<>(entry, 120);
            return ((Entry) dialog.getResultConverter().call(ButtonType.OK)).getVlcOpts();
        });

        assertThat(edited.keySet()).containsExactly("network-caching", "http-user-agent", "adaptive-logic");
    }

    @Test
    void testAnEntryWithoutVlcOptions() throws Exception {
        Entry entry = entry();
        entry.setVlcOpts(null);

        int rows = onFxThread(() -> ((TableView<?>) new EntryDialogController<>(entry, 120).getDialogPane()
                .lookup("#vlcOptionsTableView")).getItems().size());

        assertThat(rows).isZero();
    }

    @Test
    void testTheLogoIsPreviewed(@TempDir Path dir) throws Exception {
        Path logo = dir.resolve("logo.png");
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB), "png", logo.toFile());
        Entry entry = entry();
        entry.setTvgLogo(logo.toUri().toString());

        String previewed = onFxThread(() -> {
            EntryDialogController<Entry> dialog = new EntryDialogController<>(entry, 120);
            return dialog.logoPreview().getImage() == null ? null : dialog.logoPreview().getImage().getUrl();
        });

        assertThat(previewed).endsWith("/logo.png");
    }
}

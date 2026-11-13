package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EpgUrlDialogTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @Test
    void testUrlsAreReadLineByLine() {
        assertThat(EpgUrlDialog.parseUrls(" http://epg/a.xml \n\nhttp://epg/b.xml.gz\nhttp://epg/a.xml\n"))
                .containsExactly("http://epg/a.xml", "http://epg/b.xml.gz");
        assertThat(EpgUrlDialog.parseUrls("")).isEmpty();
        assertThat(EpgUrlDialog.parseUrls(null)).isEmpty();
    }

    @Test
    void testInvalidUrlsAreReported() {
        assertThat(EpgUrlDialog.invalidUrls("https://epg.example.org/de.xml.gz\nfile:///home/user/guide.xml"))
                .isEmpty();
        assertThat(EpgUrlDialog.invalidUrls("guide.xml\nhttp://epg/a.xml,http://epg/b.xml\nmailto:a@b.c\nhttp://"))
                .containsExactly("guide.xml", "http://epg/a.xml,http://epg/b.xml", "mailto:a@b.c", "http://");
    }

    @Test
    void testOkIsDisabledForInvalidUrls() throws Exception {
        CompletableFuture<List<Boolean>> disabled = new CompletableFuture<>();
        Platform.runLater(() -> {
            EpgUrlDialog dialog = new EpgUrlDialog(List.of("http://epg/a.xml"), List.of());
            Node okButton = dialog.getDialogPane().lookupButton(dialog.getOkButtonType());
            boolean initially = okButton.isDisabled();
            dialog.getUrlTextArea().setText("no url");
            boolean invalid = okButton.isDisabled();
            disabled.complete(List.of(initially, invalid));
        });

        assertThat(disabled.get(10, TimeUnit.SECONDS)).containsExactly(false, true);
    }
}

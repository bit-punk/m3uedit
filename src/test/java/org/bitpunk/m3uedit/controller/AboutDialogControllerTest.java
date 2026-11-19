package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import org.bitpunk.m3uedit.util.AppVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AboutDialogControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @Test
    void testTheVersionOfTheBuildIsShown() throws Exception {
        CompletableFuture<String> text = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                text.complete(new AboutDialogController<>().versionText().getText());
            } catch (Throwable t) {
                text.completeExceptionally(t);
            }
        });

        // the resources are filtered by Maven, the version of the pom.xml is shown
        assertThat(text.get(10, TimeUnit.SECONDS)).isEqualTo("version: " + AppVersion.version())
                .doesNotContain("${").doesNotContain("unknown");
    }
}

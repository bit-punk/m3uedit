package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.plugins.AbstractPluginService;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.PluginJars;
import org.bitpunk.m3uedit.testsupport.ExamplePluginJar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PluginsDialogTest {

    @TempDir
    Path extensions;

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @Test
    void testThePluginsAndTheJarsNotLoadedAreListed() throws Exception {
        ExamplePluginJar.build(extensions, "radio-filter.jar");
        Files.writeString(extensions.resolve("broken.jar"), "not a jar");
        PluginJars jars = PluginJars.load(extensions, getClass().getClassLoader());
        List<PluginDescriptor<?>> plugins = new ArrayList<>(AbstractPluginService.of(Filter.class, jars)
                .getDescriptors());

        CompletableFuture<List<List<String>>> shown = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                PluginsDialog dialog = new PluginsDialog(plugins, jars);
                List<String> radio = dialog.getPluginTable().getColumns().stream()
                        .map(column -> String.valueOf(column.getCellData(plugins.getLast()))).toList();
                List<String> builtIn = dialog.getPluginTable().getColumns().stream()
                        .map(column -> String.valueOf(column.getCellData(plugins.getFirst()))).toList();
                List<String> problem = dialog.getProblemTable().getColumns().stream()
                        .map(column -> String.valueOf(column.getCellData(0))).toList();
                shown.complete(List.of(radio, builtIn, problem));
            } catch (Throwable t) {
                shown.completeExceptionally(t);
            }
        });
        List<List<String>> rows = shown.get(10, TimeUnit.SECONDS);

        assertThat(rows.get(0)).containsExactly("Radio Filter (example)", "Filter", ExamplePluginJar.RADIO_FILTER,
                "radio-filter.jar");
        assertThat(rows.get(1)).endsWith("built-in");
        assertThat(rows.get(2).get(0)).isEqualTo("broken.jar");
        assertThat(rows.get(2).get(1)).startsWith("not a jar");
    }
}

package org.bitpunk.m3uedit.plugins.enricher.epg;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class EpgEnricherConfigDialogTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private static <T> T onFxThread(Function<EpgEnricherConfigDialog, T> action, EpgEnricher enricher) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.apply(new EpgEnricherConfigDialog(enricher)));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test
    void testConfigurationIsShownAndReturned() throws Exception {
        EpgEnricher enricher = new EpgEnricher();
        enricher.setSources(List.of("https://epg.example.org/de.xml.gz"));
        enricher.setStrategies(List.of(MatchStrategy.TVG_ID, MatchStrategy.NAME));
        enricher.setUrlRules(List.of(new UrlRule("service=514", "ZDF.de")));
        enricher.setCacheHours(6);

        EpgEnricherConfigDialog.Config config = onFxThread(dialog -> {
            assertThat(dialog.getStrategyCheckBoxes().get(MatchStrategy.URL_RULE).isSelected()).isFalse();
            dialog.getSourcesTextArea().appendText("\nfile:///home/user/guide.xml\n");
            dialog.getStrategyCheckBoxes().get(MatchStrategy.URL_RULE).setSelected(true);
            return dialog.toConfig();
        }, enricher);

        assertThat(config.sources()).containsExactly("https://epg.example.org/de.xml.gz", "file:///home/user/guide.xml");
        assertThat(config.strategies()).containsExactly(MatchStrategy.TVG_ID, MatchStrategy.URL_RULE, MatchStrategy.NAME);
        assertThat(config.urlRules()).containsExactly(new UrlRule("service=514", "ZDF.de"));
        assertThat(config.cacheHours()).isEqualTo(6);
    }

    @Test
    void testInvalidInputIsReportedAndDisablesOk() throws Exception {
        List<Object> result = onFxThread(dialog -> {
            dialog.getSourcesTextArea().setText("guide.xml");
            dialog.getUrlRulesTextArea().setText("(broken => X");
            dialog.getStrategyCheckBoxes().values().forEach(checkBox -> checkBox.setSelected(false));
            boolean disabled = dialog.getDialogPane().lookupButton(dialog.getOkButtonType()).isDisabled();
            return List.of(disabled, dialog.errors());
        }, new EpgEnricher());

        assertThat(result.get(0)).isEqualTo(true);
        // the url rules are not checked while matching by url rule is switched off
        assertThat(result.get(1).toString()).contains("Not a valid source URL: guide.xml")
                .contains("Select at least one way").doesNotContain("Invalid expression");
    }

    @Test
    void testDefaultSourcesCanBeRestored() throws Exception {
        EpgEnricher enricher = new EpgEnricher();
        enricher.setSources(List.of("https://epg.example.org/de.xml.gz"));

        EpgEnricherConfigDialog.Config config = onFxThread(dialog -> {
            dialog.getDefaultSourcesButton().fire();
            return dialog.toConfig();
        }, enricher);

        assertThat(config.sources()).hasSize(3).containsExactlyElementsOf(EpgEnricher.defaultSources());
    }
}

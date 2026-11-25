package org.bitpunk.m3uedit.plugins.enricher.epg;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import lombok.Getter;
import org.bitpunk.m3uedit.api.PluginContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configures the {@link EpgEnricher}: its sources, the strategies matching the entries and the url rules.
 */
public class EpgEnricherConfigDialog extends Dialog<EpgEnricherConfigDialog.Config> {
    private static final Set<String> SCHEMES = Set.of("http", "https", "file");

    /**
     * The configuration entered.
     */
    public record Config(List<String> sources, List<MatchStrategy> strategies, List<UrlRule> urlRules, int cacheHours,
                         boolean compileGuide, int guideDaysAhead, int guideHoursPast, boolean compressGuide) {
    }

    @Getter
    private final TextArea sourcesTextArea = new TextArea();
    @Getter
    private Button defaultSourcesButton;
    @Getter
    private final TextArea urlRulesTextArea = new TextArea();
    @Getter
    private final Map<MatchStrategy, CheckBox> strategyCheckBoxes = new EnumMap<>(MatchStrategy.class);
    @Getter
    private final Spinner<Integer> cacheHoursSpinner = new Spinner<>(1, 24 * 7, EpgEnricher.DEFAULT_CACHE_HOURS);
    @Getter
    private final CheckBox compileGuideCheckBox;
    @Getter
    private final Spinner<Integer> guideDaysAheadSpinner = new Spinner<>(0, 31, EpgEnricher.DEFAULT_GUIDE_DAYS_AHEAD);
    @Getter
    private final Spinner<Integer> guideHoursPastSpinner = new Spinner<>(0, 24 * 7, EpgEnricher.DEFAULT_GUIDE_HOURS_PAST);
    @Getter
    private final CheckBox compressGuideCheckBox;
    @Getter
    private final Label errorLabel = new Label();
    @Getter
    private final ButtonType okButtonType;
    private final PluginContext context;

    /**
     * @param context the context of the enricher, for the texts and the default sources
     */
    public EpgEnricherConfigDialog(EpgEnricher enricher, PluginContext context) {
        this.context = context;
        compileGuideCheckBox = new CheckBox(context.text("epgEnricher.config.compileGuide"));
        compressGuideCheckBox = new CheckBox(context.text("epgEnricher.config.compressGuide"));
        okButtonType = new ButtonType(context.text("common.ok"), ButtonBar.ButtonData.OK_DONE);
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle(enricher.getName());
        setHeaderText(context.text("epgEnricher.config.header"));

        sourcesTextArea.setPromptText("https://example.org/guide-de.xml.gz");
        sourcesTextArea.setPrefRowCount(5);
        sourcesTextArea.setPrefColumnCount(60);
        sourcesTextArea.setText(String.join("\n", nullToEmpty(enricher.getSources())));

        HBox strategyBox = new HBox(12);
        List<MatchStrategy> enabled = nullToEmpty(enricher.getStrategies());
        for (MatchStrategy strategy : MatchStrategy.values()) {
            CheckBox checkBox = new CheckBox(strategy.getLabel(context));
            checkBox.setSelected(enabled.contains(strategy));
            strategyCheckBoxes.put(strategy, checkBox);
            strategyBox.getChildren().add(checkBox);
        }

        urlRulesTextArea.setPromptText("service=514 " + UrlRule.SEPARATOR + " ZDF.de");
        urlRulesTextArea.setPrefRowCount(4);
        urlRulesTextArea.setText(String.join("\n", nullToEmpty(enricher.getUrlRules()).stream().map(UrlRule::format).toList()));
        urlRulesTextArea.disableProperty().bind(strategyCheckBoxes.get(MatchStrategy.URL_RULE).selectedProperty().not());

        cacheHoursSpinner.getValueFactory().setValue(enricher.getCacheHours() > 0 ? enricher.getCacheHours()
                : EpgEnricher.DEFAULT_CACHE_HOURS);
        cacheHoursSpinner.setEditable(true);
        cacheHoursSpinner.setPrefWidth(90);

        compileGuideCheckBox.setSelected(enricher.isCompileGuide());
        guideDaysAheadSpinner.getValueFactory().setValue(Math.max(0, enricher.getGuideDaysAhead()));
        guideHoursPastSpinner.getValueFactory().setValue(Math.max(0, enricher.getGuideHoursPast()));
        compressGuideCheckBox.setSelected(enricher.isCompressGuide());
        for (Spinner<Integer> spinner : List.of(guideDaysAheadSpinner, guideHoursPastSpinner)) {
            spinner.setEditable(true);
            spinner.setPrefWidth(90);
            spinner.disableProperty().bind(compileGuideCheckBox.selectedProperty().not());
        }
        compressGuideCheckBox.disableProperty().bind(compileGuideCheckBox.selectedProperty().not());

        errorLabel.setStyle("-fx-text-fill: -app-error;");
        errorLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(4));
        int row = 0;
        defaultSourcesButton = new Button(context.text("epgEnricher.config.defaultSources"));
        defaultSourcesButton.setTooltip(new javafx.scene.control.Tooltip(
                context.text("epgEnricher.config.defaultSources.tooltip",
                        "~/.m3uedit/plugins/" + EpgEnricher.CONFIG_FILE_NAME)));
        defaultSourcesButton.setOnAction(event ->
                sourcesTextArea.setText(String.join("\n", EpgEnricher.defaultSources(context))));
        javafx.scene.layout.VBox sourcesLabel = new javafx.scene.layout.VBox(6,
                new Label(context.text("epgEnricher.config.sources")), defaultSourcesButton);
        sourcesLabel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        grid.add(sourcesLabel, 0, row);
        grid.add(sourcesTextArea, 1, row++);
        grid.add(hint(context.text("epgEnricher.config.sources.hint")), 1, row++);
        grid.add(new Label(context.text("epgEnricher.config.strategies")), 0, row);
        grid.add(strategyBox, 1, row++);
        grid.add(hint(context.text("epgEnricher.config.strategies.hint")), 1, row++);
        grid.add(new Label(context.text("epgEnricher.config.urlRules")), 0, row);
        grid.add(urlRulesTextArea, 1, row++);
        grid.add(hint(context.text("epgEnricher.config.urlRules.hint", UrlRule.SEPARATOR)), 1, row++);
        grid.add(new Label(context.text("epgEnricher.config.cacheHours")), 0, row);
        grid.add(new HBox(6, cacheHoursSpinner, new Label(context.text("epgEnricher.config.cacheHours.unit"))), 1,
                row++);
        grid.add(new Label(context.text("epgEnricher.config.guide")), 0, row);
        grid.add(compileGuideCheckBox, 1, row++);
        HBox window = new HBox(6, new Label(context.text("epgEnricher.config.guideWindow.ahead")), guideDaysAheadSpinner,
                new Label(context.text("epgEnricher.config.guideWindow.past")), guideHoursPastSpinner,
                new Label(context.text("epgEnricher.config.guideWindow.end")));
        window.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        grid.add(window, 1, row++);
        grid.add(compressGuideCheckBox, 1, row++);
        grid.add(hint(context.text("epgEnricher.config.guide.hint")), 1, row++);
        grid.add(errorLabel, 1, row);
        GridPane.setHgrow(sourcesTextArea, Priority.ALWAYS);
        GridPane.setVgrow(sourcesTextArea, Priority.ALWAYS);
        getDialogPane().setContent(grid);
        // not ButtonType.CANCEL: its text follows the language of the system, not the one of the application
        getDialogPane().getButtonTypes().setAll(okButtonType,
                new ButtonType(context.text("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));

        Button okButton = (Button) getDialogPane().lookupButton(okButtonType);
        sourcesTextArea.textProperty().addListener((observable, oldText, newText) -> validate(okButton));
        urlRulesTextArea.textProperty().addListener((observable, oldText, newText) -> validate(okButton));
        strategyCheckBoxes.values().forEach(checkBox -> checkBox.selectedProperty()
                .addListener((observable, oldValue, newValue) -> validate(okButton)));
        validate(okButton);
        setResultConverter(buttonType -> buttonType == okButtonType ? toConfig() : null);
    }

    private static Label hint(String text) {
        Label hint = new Label(text);
        hint.setWrapText(true);
        hint.setStyle("-fx-opacity: 0.7;");
        return hint;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private void validate(Button okButton) {
        List<String> errors = errors();
        okButton.setDisable(!errors.isEmpty());
        errorLabel.setText(String.join("\n", errors));
    }

    List<String> errors() {
        List<String> errors = new ArrayList<>();
        for (String source : lines(sourcesTextArea.getText())) {
            if (!isValidSource(source)) {
                errors.add(context.text("epgEnricher.config.error.invalidSource", source));
            }
        }
        if (strategyCheckBoxes.get(MatchStrategy.URL_RULE).isSelected()) {
            for (String line : lines(urlRulesTextArea.getText())) {
                try {
                    UrlRule.parse(line, context);
                } catch (IllegalArgumentException e) {
                    errors.add(e.getMessage());
                }
            }
        }
        if (strategyCheckBoxes.values().stream().noneMatch(CheckBox::isSelected)) {
            errors.add(context.text("epgEnricher.config.error.noStrategy"));
        }
        return errors;
    }

    Config toConfig() {
        List<MatchStrategy> strategies = strategyCheckBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected()).map(Map.Entry::getKey).toList();
        // rules are kept while the strategy is switched off
        List<UrlRule> rules = lines(urlRulesTextArea.getText()).stream().map(line -> {
            try {
                return UrlRule.parse(line, context);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();
        return new Config(lines(sourcesTextArea.getText()), strategies, rules, cacheHoursSpinner.getValue(),
                compileGuideCheckBox.isSelected(), guideDaysAheadSpinner.getValue(), guideHoursPastSpinner.getValue(),
                compressGuideCheckBox.isSelected());
    }

    static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).distinct().toList();
    }

    private static boolean isValidSource(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            return SCHEMES.contains(scheme) && (uri.getHost() != null || "file".equals(scheme));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}

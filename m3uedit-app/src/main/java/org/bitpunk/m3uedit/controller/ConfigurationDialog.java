package org.bitpunk.m3uedit.controller;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import lombok.Getter;
import org.bitpunk.m3uedit.config.Configuration;
import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.config.Language;
import org.bitpunk.m3uedit.config.StartupMode;
import org.bitpunk.m3uedit.config.Theme;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.player.PlayerArguments;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The general settings of m3uedit, opened by <i>File → Configuration…</i>: <i>OK</i> applies and saves them,
 * <i>Cancel</i> discards them, also the lists marked to be cleared.
 */
public class ConfigurationDialog extends Dialog<ConfigurationDialog.Result> {
    static final int MAX_RECENT_PROJECTS = 50;

    /**
     * The settings entered and the collected data to clear.
     */
    public record Result(Configuration.Values values, Set<String> toClear) {
        public Result {
            toClear = toClear.isEmpty() ? Set.of() : Set.copyOf(toClear);
        }
    }

    @Getter
    private final ComboBox<Language> languageComboBox = new ComboBox<>();
    @Getter
    private final ComboBox<Theme> themeComboBox = new ComboBox<>();
    @Getter
    private final TextField playerCommandField = new TextField();
    @Getter
    private final Button choosePlayerButton = new Button(Messages.get("common.choose"));
    @Getter
    private final TextField playerArgumentsField = new TextField();
    @Getter
    private final CheckBox passVlcOptionsCheckBox = new CheckBox(Messages.get("configuration.player.passVlcOptions"));
    @Getter
    private final Spinner<Integer> recentProjectsSpinner = new Spinner<>(0, MAX_RECENT_PROJECTS, 10);
    @Getter
    private final Map<String, Button> clearButtons = new LinkedHashMap<>();
    @Getter
    private final Map<String, Label> collectedLabels = new LinkedHashMap<>();
    @Getter
    private final RadioButton startScreenRadioButton = new RadioButton(Messages.get("configuration.startup.startScreen"));
    @Getter
    private final RadioButton reopenProjectsRadioButton =
            new RadioButton(Messages.get("configuration.startup.reopenProjects"));
    @Getter
    private final ButtonType okButtonType = new ButtonType(Messages.get("common.ok"), ButtonBar.ButtonData.OK_DONE);
    private final Set<String> toClear = new LinkedHashSet<>();

    public ConfigurationDialog(Configuration configuration) {
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle(Messages.get("configuration.title"));
        setHeaderText(null);
        getDialogPane().getStyleClass().add("configuration-dialog");
        getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/fxml/css/configuration-dialog.css")).toExternalForm());

        Configuration.Values values = configuration.load();
        languageComboBox.getItems().setAll(Language.values());
        languageComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Language language) {
                return language == null ? "" : languageName(language);
            }

            @Override
            public Language fromString(String text) {
                return null;
            }
        });
        languageComboBox.setValue(values.language());
        themeComboBox.getItems().setAll(Theme.values());
        themeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme theme) {
                return theme == null ? "" : switch (theme) {
                    case LIGHT -> Messages.get("configuration.theme.light");
                    case DARK -> Messages.get("configuration.theme.dark");
                    case SYSTEM -> Messages.get("configuration.theme.system");
                };
            }

            @Override
            public Theme fromString(String text) {
                return null;
            }
        });
        themeComboBox.setValue(values.theme());
        playerCommandField.setText(values.playerCommand());
        playerCommandField.setPromptText(Messages.get("configuration.player.command.prompt"));
        choosePlayerButton.setOnAction(event -> choosePlayer());
        playerArgumentsField.setText(PlayerArguments.join(values.playerArguments()));
        playerArgumentsField.setPromptText(Messages.get("configuration.player.arguments.prompt"));
        passVlcOptionsCheckBox.setSelected(values.passVlcOptions());
        recentProjectsSpinner.getValueFactory().setValue(Math.clamp(values.maxRecentProjects(), 0, MAX_RECENT_PROJECTS));
        recentProjectsSpinner.setEditable(true);
        recentProjectsSpinner.getStyleClass().add("short-field");
        ToggleGroup startup = new ToggleGroup();
        startScreenRadioButton.setToggleGroup(startup);
        reopenProjectsRadioButton.setToggleGroup(startup);
        (values.startup() == StartupMode.REOPEN_PROJECTS ? reopenProjectsRadioButton : startScreenRadioButton)
                .setSelected(true);

        GridPane form = new GridPane();
        form.getStyleClass().add("configuration-form");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(170);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labels, fields);
        int row = 0;

        form.add(section(Messages.get("configuration.section.appearance")), 0, row++, 2, 1);
        form.addRow(row++, field(Messages.get("configuration.theme")), themeComboBox);
        form.addRow(row++, field(Messages.get("configuration.language")), withHint(languageComboBox,
                Messages.get("configuration.language.hint")));

        form.add(section(Messages.get("configuration.section.externalPlayer")), 0, row++, 2, 1);
        HBox commandRow = new HBox(playerCommandField, choosePlayerButton);
        commandRow.getStyleClass().add("field-row");
        HBox.setHgrow(playerCommandField, Priority.ALWAYS);
        form.addRow(row++, field(Messages.get("configuration.player.command")), commandRow);
        form.addRow(row++, field(Messages.get("configuration.player.arguments")), withHint(playerArgumentsField,
                Messages.get("configuration.player.arguments.hint")));
        form.add(withHint(passVlcOptionsCheckBox, Messages.get("configuration.player.passVlcOptions.hint")),
                1, row++);

        form.add(section(Messages.get("configuration.section.recentlyUsed")), 0, row++, 2, 1);
        HBox recentRow = new HBox(recentProjectsSpinner, hint(Messages.get("configuration.recent.max.hint")));
        recentRow.getStyleClass().add("field-row");
        form.addRow(row++, field(Messages.get("configuration.recent.max")), recentRow);

        // the recently used projects and the data collected by the plugins, e.g. downloaded EPG sources
        form.add(section(Messages.get("configuration.section.collected")), 0, row++, 2, 1);
        for (CollectedData data : configuration.collectedData()) {
            HBox collected = collected(data);
            form.addRow(row++, field(data.getName()), data.getHint() == null ? collected
                    : withHint(collected, data.getHint()));
        }

        form.add(section(Messages.get("configuration.section.startup")), 0, row++, 2, 1);
        form.add(new VBox(6, startScreenRadioButton, reopenProjectsRadioButton), 0, row, 2, 1);

        getDialogPane().setContent(form);
        // in the language of the application, not in the language of the system
        getDialogPane().getButtonTypes().addAll(okButtonType,
                new ButtonType(Messages.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        getDialogPane().setPrefWidth(640);

        setResultConverter(buttonType -> buttonType == okButtonType ? result() : null);
    }

    /**
     * @return the settings entered, also a number typed into the spinner without pressing Enter
     */
    Result result() {
        int maxRecentProjects;
        try {
            maxRecentProjects = Math.clamp(Integer.parseInt(recentProjectsSpinner.getEditor().getText().strip()), 0,
                    MAX_RECENT_PROJECTS);
        } catch (NumberFormatException e) {
            maxRecentProjects = recentProjectsSpinner.getValue();
        }
        return new Result(new Configuration.Values(playerCommandField.getText(),
                PlayerArguments.split(playerArgumentsField.getText()), passVlcOptionsCheckBox.isSelected(),
                maxRecentProjects, reopenProjectsRadioButton.isSelected() ? StartupMode.REOPEN_PROJECTS
                : StartupMode.START_SCREEN, languageComboBox.getValue(), themeComboBox.getValue()), toClear);
    }

    /**
     * Marks the data to be cleared when <i>OK</i> is pressed.
     */
    void markToClear(String id) {
        toClear.add(id);
        collectedLabels.get(id).setText(Messages.get("configuration.cleared"));
        clearButtons.get(id).setDisable(true);
    }

    private HBox collected(CollectedData data) {
        Label label = new Label(data.describe());
        label.getStyleClass().add("collected-label");
        Button clear = new Button(Messages.get("common.clear"));
        clear.setDisable(data.isEmpty());
        clear.setOnAction(event -> markToClear(data.getId()));
        collectedLabels.put(data.getId(), label);
        clearButtons.put(data.getId(), clear);
        HBox box = new HBox(label, clear);
        box.getStyleClass().add("field-row");
        return box;
    }

    private void choosePlayer() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("configuration.player.chooser.title"));
        File current = new File(playerCommandField.getText().strip());
        if (current.getParentFile() != null && current.getParentFile().isDirectory()) {
            chooser.setInitialDirectory(current.getParentFile());
        }
        File chosen = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (chosen != null) {
            playerCommandField.setText(chosen.getAbsolutePath());
        }
    }

    /**
     * @return the name of the language in the language itself, so it is found by someone not understanding the
     * current one; only the default of the system is named in the current language
     */
    static String languageName(Language language) {
        return switch (language) {
            case SYSTEM -> Messages.get("configuration.language.system");
            case ENGLISH -> "English";
            case GERMAN -> "Deutsch";
        };
    }

    private static Label section(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Label field(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-hint");
        label.setWrapText(true);
        return label;
    }

    private static VBox withHint(javafx.scene.Node node, String hint) {
        return new VBox(2, node, hint(hint));
    }
}

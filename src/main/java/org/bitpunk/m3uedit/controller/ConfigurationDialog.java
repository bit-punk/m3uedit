package org.bitpunk.m3uedit.controller;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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
import lombok.Getter;
import org.bitpunk.m3uedit.config.Configuration;
import org.bitpunk.m3uedit.config.Configuration.Collected;
import org.bitpunk.m3uedit.config.StartupMode;
import org.bitpunk.m3uedit.player.PlayerArguments;

import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
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
    public record Result(Configuration.Values values, Set<Collected> toClear) {
        public Result {
            toClear = toClear.isEmpty() ? Set.of() : Set.copyOf(toClear);
        }
    }

    @Getter
    private final TextField playerCommandField = new TextField();
    @Getter
    private final Button choosePlayerButton = new Button("Choose…");
    @Getter
    private final TextField playerArgumentsField = new TextField();
    @Getter
    private final CheckBox passVlcOptionsCheckBox = new CheckBox("Pass the VLC options of the entries");
    @Getter
    private final Spinner<Integer> recentProjectsSpinner = new Spinner<>(0, MAX_RECENT_PROJECTS, 10);
    @Getter
    private final Map<Collected, Button> clearButtons = new EnumMap<>(Collected.class);
    @Getter
    private final Map<Collected, Label> collectedLabels = new EnumMap<>(Collected.class);
    @Getter
    private final RadioButton startScreenRadioButton = new RadioButton("Show the start screen");
    @Getter
    private final RadioButton reopenProjectsRadioButton =
            new RadioButton("Reopen the projects that were open when m3uedit was closed");
    @Getter
    private final ButtonType okButtonType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
    private final Set<Collected> toClear = EnumSet.noneOf(Collected.class);

    public ConfigurationDialog(Configuration configuration) {
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle("Configuration");
        setHeaderText(null);
        getDialogPane().getStyleClass().add("configuration-dialog");
        getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/fxml/css/configuration-dialog.css")).toExternalForm());

        Configuration.Values values = configuration.load();
        playerCommandField.setText(values.playerCommand());
        playerCommandField.setPromptText("e.g. vlc, mpv or the path of the program");
        choosePlayerButton.setOnAction(event -> choosePlayer());
        playerArgumentsField.setText(PlayerArguments.join(values.playerArguments()));
        playerArgumentsField.setPromptText("e.g. --fullscreen");
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

        form.add(section("External player"), 0, row++, 2, 1);
        HBox commandRow = new HBox(playerCommandField, choosePlayerButton);
        commandRow.getStyleClass().add("field-row");
        HBox.setHgrow(playerCommandField, Priority.ALWAYS);
        form.addRow(row++, field("Command"), commandRow);
        form.addRow(row++, field("Additional arguments"), withHint(playerArgumentsField,
                "separated by spaces, put arguments containing spaces in double quotes"));
        form.add(withHint(passVlcOptionsCheckBox, "only understood by VLC: the options are given as :option=value"),
                1, row++);

        form.add(section("Recently used"), 0, row++, 2, 1);
        HBox recentRow = new HBox(recentProjectsSpinner, hint("0 turns the list off"));
        recentRow.getStyleClass().add("field-row");
        form.addRow(row++, field("Projects remembered"), recentRow);
        form.addRow(row++, field("Projects"), collected(configuration, Collected.RECENT_PROJECTS));
        form.addRow(row++, field("SFTP servers"), collected(configuration, Collected.SFTP_SERVERS));
        form.addRow(row++, field("FritzBoxes"), collected(configuration, Collected.FRITZBOXES));

        form.add(section("EPG cache"), 0, row++, 2, 1);
        form.addRow(row++, field("Downloaded sources"), withHint(collected(configuration, Collected.EPG_CACHE),
                "cleared sources are downloaded again when they are needed"));

        form.add(section("Startup"), 0, row++, 2, 1);
        form.add(new VBox(6, startScreenRadioButton, reopenProjectsRadioButton), 0, row, 2, 1);

        getDialogPane().setContent(form);
        // in English like the rest of the application, not in the language of the system
        getDialogPane().getButtonTypes().addAll(okButtonType,
                new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
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
                : StartupMode.START_SCREEN), toClear);
    }

    /**
     * Marks the data to be cleared when <i>OK</i> is pressed.
     */
    void markToClear(Collected collected) {
        toClear.add(collected);
        collectedLabels.get(collected).setText("cleared when OK is pressed");
        clearButtons.get(collected).setDisable(true);
    }

    private HBox collected(Configuration configuration, Collected collected) {
        Label label = new Label(configuration.describe(collected));
        label.getStyleClass().add("collected-label");
        Button clear = new Button("Clear");
        clear.setDisable(configuration.isEmpty(collected));
        clear.setOnAction(event -> markToClear(collected));
        collectedLabels.put(collected, label);
        clearButtons.put(collected, clear);
        HBox box = new HBox(label, clear);
        box.getStyleClass().add("field-row");
        return box;
    }

    private void choosePlayer() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the external player");
        File current = new File(playerCommandField.getText().strip());
        if (current.getParentFile() != null && current.getParentFile().isDirectory()) {
            chooser.setInitialDirectory(current.getParentFile());
        }
        File chosen = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (chosen != null) {
            playerCommandField.setText(chosen.getAbsolutePath());
        }
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

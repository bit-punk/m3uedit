package org.bitpunk.m3uedit.plugins.filter.reachableurl;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.plugins.support.PluginDialogController;
import org.bitpunk.m3uedit.plugins.filter.reachableurl.model.ReachableUrlFilterConfig;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Dialog to configure the timeout of the {@link ReachableUrlFilter}.
 * The configuration is persisted to the filter's configuration-file when the dialog is confirmed.
 */
@Slf4j
public class ReachableUrlFilterConfigDialogController extends PluginDialogController<ReachableUrlFilterConfig> {
    public static final String FXML_PLUGIN_FILTER_CONFIG = "plugin/filter/reachableurl/ReachableUrlFilterConfigDialog.fxml";
    public static final int MIN_TIMEOUT_MILLIS = 100;
    public static final int MAX_TIMEOUT_MILLIS = 60_000;
    public static final int STEP_MILLIS = 100;

    @FXML
    @Getter
    private Spinner<Integer> timeoutSpinner;

    @FXML
    @Getter
    private Label hintLabel;

    @FXML
    @Getter
    private ButtonType okButtonType;

    @Getter
    private final ReachableUrlFilter filter;

    /**
     * @param filter the filter to configure, initialised with its context
     */
    public ReachableUrlFilterConfigDialogController(ReachableUrlFilter filter) {
        super(Objects.requireNonNull(filter, "filter must not be null").getContext(), FXML_PLUGIN_FILTER_CONFIG,
                filter.getContext().text("filter.reachableUrl.config.title"));
        this.filter = filter;
        timeoutSpinner.getValueFactory().setValue(filter.getTimeoutMillis());
        getDialogPane().lookupButton(okButtonType).disableProperty().bind(isEditorTextValid().not());
        setResultConverter(buttonType -> {
            if (buttonType != null && Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                return applyAndPersistConfig();
            }
            return null;
        });
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS, ReachableUrlFilterConfig.DEFAULT_TIMEOUT_MILLIS, STEP_MILLIS));
        // commit typed values when the spinner loses the focus, not only on <Enter>
        timeoutSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitEditorText();
            }
        });
    }

    private BooleanBinding isEditorTextValid() {
        return Bindings.createBooleanBinding(() -> parseEditorText() != null, timeoutSpinner.getEditor().textProperty());
    }

    /**
     * @return the value typed into the spinner's editor, or null if it is not a number within the allowed range
     */
    Integer parseEditorText() {
        String text = timeoutSpinner.getEditor().getText();
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.strip());
            return value >= MIN_TIMEOUT_MILLIS && value <= MAX_TIMEOUT_MILLIS ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void commitEditorText() {
        Integer value = parseEditorText();
        if (value != null) {
            timeoutSpinner.getValueFactory().setValue(value);
        } else {
            // restore the last valid value
            timeoutSpinner.getEditor().setText(String.valueOf(timeoutSpinner.getValue()));
        }
    }

    /**
     * Writes the timeout shown in the dialog to the filter's configuration-file.
     *
     * @return the persisted configuration
     */
    ReachableUrlFilterConfig applyAndPersistConfig() {
        commitEditorText();
        ReachableUrlFilterConfig config = ReachableUrlFilterConfig.builder()
                .timeoutMillis(timeoutSpinner.getValue())
                .build();
        log.debug("saving configuration of ReachableUrlFilter: {}", config);
        filter.saveConfig(config);
        return config;
    }
}

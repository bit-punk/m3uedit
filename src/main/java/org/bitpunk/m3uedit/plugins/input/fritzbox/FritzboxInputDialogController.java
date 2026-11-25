package org.bitpunk.m3uedit.plugins.input.fritzbox;

import jakarta.validation.ConstraintViolation;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Callback;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.bitpunk.m3uedit.util.PojoValidator;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

@Slf4j
public class FritzboxInputDialogController extends AbstractDialogController<FritzBoxConnection> implements Initializable {
    public static final String FXML_PLUGIN_INPUT_FRITZBOX = "plugin/input/fritzbox/FritzboxInputDialog.fxml";
    private final PluginContext context;
    @Getter
    private final FritzBoxConnection fritzBoxConnection = new FritzBoxConnection();
    @FXML
    public ButtonType okButtonType;
    @Getter
    private BooleanBinding inputsFull;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private TextField baseUrlTextField;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private ListView<StreamSource> availableStreamSources;
    @FXML
    private ComboBox<FritzBoxConnection> recentConnectionsComboBox;
    @FXML
    private Button removeRecentConnectionButton;
    /**
     * Whether a stream is imported, per stream shown (all by default). Kept here, not in the cells, which are re-used.
     * Created by {@link #initialize}, which the FXML loader calls before the fields of this class are initialized.
     */
    private Map<StreamSource, BooleanProperty> streamSelected;
    private RecentFritzBoxConnections recentConnections;

    /**
     * @param context the context of the input, e.g. for its texts and its recently used connections
     */
    public FritzboxInputDialogController(PluginContext context) {
        // set before the FXML is loaded, which calls initialize()
        this.context = context;
        super(FXML_PLUGIN_INPUT_FRITZBOX, context.text("fritzbox.dialog.title"));
        setResultConverter(getURIResultConverter());
    }

    private Callback<ButtonType, FritzBoxConnection> getURIResultConverter() {
        return buttonType -> {
            FritzBoxConnection fritzBoxConnection = null;
            if (Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed");
                fritzBoxConnection = createFritzboxConnectionFromInput();
            }
            log.debug("returning result: {}", fritzBoxConnection);
            return fritzBoxConnection;
        };
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeStreamSourceView();
        initializeBinding();
        initializeRecentConnections(new RecentFritzBoxConnections(context));
    }

    private void initializeStreamSourceView() {
        streamSelected = new LinkedHashMap<>();
        for (StreamSource streamSource : List.of(new StreamSource("tvsd.m3u", "SD streams"),
                new StreamSource("tvhd.m3u", "HD streams"))) {
            // import all streams per default
            streamSelected.put(streamSource, new SimpleBooleanProperty(true));
            availableStreamSources.getItems().add(streamSource);
        }
        setCellFactory(availableStreamSources);
    }

    /**
     * Offers the FritzBox connections read successfully before, see {@link RecentFritzBoxConnections}.
     */
    void initializeRecentConnections(RecentFritzBoxConnections recent) {
        recentConnections = recent;
        recentConnectionsComboBox.getItems().setAll(recent.getConnections());
        recentConnectionsComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(FritzBoxConnection connection) {
                return connection == null ? null : describe(connection);
            }

            @Override
            public FritzBoxConnection fromString(String text) {
                return recentConnectionsComboBox.getValue();
            }
        });
        recentConnectionsComboBox.disableProperty().bind(Bindings.isEmpty(recentConnectionsComboBox.getItems()));
        recentConnectionsComboBox.setOnAction(event -> applyRecentConnection(recentConnectionsComboBox.getValue()));
        removeRecentConnectionButton.disableProperty().bind(recentConnectionsComboBox.valueProperty().isNull());
    }

    /**
     * @return e.g. "http://fritz.box/dvb/m3u/  (SD, HD)"
     */
    private String describe(FritzBoxConnection connection) {
        List<String> streams = connection.getSelectedStreamSources() == null ? List.of()
                : connection.getSelectedStreamSources().stream().map(StreamSource::getDescription)
                .filter(Objects::nonNull).map(description -> description.replace(" streams", "")).distinct().toList();
        return streams.isEmpty() ? connection.getBaseUrl()
                : "%s  (%s)".formatted(connection.getBaseUrl(), String.join(", ", streams));
    }

    /**
     * Shows the current configuration of an input configured again: its base url and selected streams.
     */
    public void setFritzBoxConnection(FritzBoxConnection connection) {
        applyRecentConnection(connection);
    }

    /**
     * Fills in the base url and the streams of a connection, e.g. a recently used one.
     */
    private void applyRecentConnection(FritzBoxConnection connection) {
        if (connection == null) {
            return;
        }
        baseUrlTextField.setText(connection.getBaseUrl());
        List<String> names = connection.getSelectedStreamSources() == null ? List.of()
                : connection.getSelectedStreamSources().stream().map(StreamSource::getName).toList();
        streamSelected.forEach((streamSource, selected) -> selected.set(names.contains(streamSource.getName())));
    }

    @FXML
    private void handleRemoveRecentConnection() {
        FritzBoxConnection selected = recentConnectionsComboBox.getValue();
        if (selected != null) {
            recentConnections.remove(selected);
            recentConnectionsComboBox.setValue(null);
            recentConnectionsComboBox.getItems().remove(selected);
        }
    }

    /**
     * @return the streams to import, in the order shown
     */
    List<StreamSource> getSelectedStreamSources() {
        return streamSelected.entrySet().stream().filter(entry -> entry.getValue().get()).map(Map.Entry::getKey)
                .toList();
    }

    private void initializeBinding() {
        inputsFull = new BooleanBinding() {
            {
                bind(
                        baseUrlTextField.textProperty()
                );
            }

            @Override
            protected boolean computeValue() {
                return isValidBean();
            }
        };
    }

    private boolean isValidBean() {
        PojoValidator<FritzBoxConnection> validator = new PojoValidator<>();
        FritzBoxConnection fritzBoxConnection = createFritzboxConnectionFromInput();
        Set<ConstraintViolation<FritzBoxConnection>> errors = validator.validate(fritzBoxConnection);
        displayValidationErrors(errors);
        log.trace("Validation-errors found: {}", errors);
        return errors.isEmpty();
    }

    private FritzBoxConnection createFritzboxConnectionFromInput() {
        fritzBoxConnection.setBaseUrl(baseUrlTextField.getText());
        fritzBoxConnection.setSelectedStreamSources(new ArrayList<>(getSelectedStreamSources()));
        return fritzBoxConnection;
    }

    private void displayValidationErrors(Set<ConstraintViolation<FritzBoxConnection>> errors) {
        resetTextFields();
        for (ConstraintViolation<FritzBoxConnection> error : errors) {
            String errorMessage = error.getMessage();
            String fieldName = error.getPropertyPath().toString();
            log.trace("Field: {} Message: {}", fieldName, errorMessage);
            if (fieldName.equals("baseUrl")) {
                markFieldInvalid(baseUrlTextField, describe(error));
            }
        }
    }

    /**
     * @return the message of a validation error in the language of the user interface
     */
    private String describe(ConstraintViolation<FritzBoxConnection> error) {
        return switch (error.getMessage()) {
            case FritzBoxConnection.BASE_URL_EMPTY -> context.text("fritzbox.invalid.baseUrlEmpty");
            case FritzBoxConnection.BASE_URL_INVALID -> context.text("fritzbox.invalid.baseUrl");
            default -> error.getMessage();
        };
    }

    /**
     * @return the description of a stream shown to the user, e.g. "SD streams" (the description stored with the
     * stream is not translated, it is part of the project-file)
     */
    String describe(StreamSource streamSource) {
        return switch (streamSource.getName()) {
            case "tvsd.m3u" -> context.text("fritzbox.stream.sd");
            case "tvhd.m3u" -> context.text("fritzbox.stream.hd");
            default -> streamSource.getDescription();
        };
    }

    private void resetTextFields() {
        resetTextField(baseUrlTextField);
    }

    private void setCellFactory(ListView<StreamSource> availableStreamsListView) {
        availableStreamsListView.setCellFactory((ListView<StreamSource> param) -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private BooleanProperty bound;

            @Override
            public void updateItem(StreamSource streamSource, boolean empty) {
                super.updateItem(streamSource, empty);
                // the cell is re-used: the check-box shows the selection of the stream shown, it does not change it
                if (bound != null) {
                    checkBox.selectedProperty().unbindBidirectional(bound);
                    bound = null;
                }
                if (empty || streamSource == null) {
                    setGraphic(null);
                    setText(null);
                    setTooltip(null);
                } else {
                    bound = streamSelected.get(streamSource);
                    checkBox.selectedProperty().bindBidirectional(bound);
                    setGraphic(checkBox);
                    setText(streamSource.getName());
                    setTooltip(new Tooltip(describe(streamSource)));
                }
            }
        });
    }

}
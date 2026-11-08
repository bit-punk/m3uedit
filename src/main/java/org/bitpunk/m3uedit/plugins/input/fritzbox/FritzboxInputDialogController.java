package org.bitpunk.m3uedit.plugins.input.fritzbox;

import jakarta.validation.ConstraintViolation;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.bitpunk.m3uedit.util.PojoValidator;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

@Slf4j
public class FritzboxInputDialogController extends AbstractDialogController<FritzBoxConnection> implements Initializable {
    public static final String FXML_PLUGIN_INPUT_FRITZBOX = "plugin/input/fritzbox/FritzboxInputDialog.fxml";
    public static final String FRITZBOX_DIALOG_TITLE = "Enter FritzBox-Inputs data";
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

    public FritzboxInputDialogController() {
        super(FXML_PLUGIN_INPUT_FRITZBOX, FRITZBOX_DIALOG_TITLE);
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
    }

    private void initializeStreamSourceView() {
        setCellFactory(availableStreamSources);
        availableStreamSources.getItems().add(new StreamSource("tvsd.m3u", "SD streams"));
        availableStreamSources.getItems().add(new StreamSource("tvhd.m3u", "HD streams"));
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
        return fritzBoxConnection;
    }

    private void displayValidationErrors(Set<ConstraintViolation<FritzBoxConnection>> errors) {
        resetTextFields();
        for (ConstraintViolation<FritzBoxConnection> error : errors) {
            String errorMessage = error.getMessage();
            String fieldName = error.getPropertyPath().toString();
            log.trace("Field: {} Message: {}", fieldName, errorMessage);
            if (fieldName.equals("baseUrl")) {
                markFieldInvalid(baseUrlTextField, errorMessage);
            }
        }
    }

    private void resetTextFields() {
        resetTextField(baseUrlTextField);
    }

    private void setCellFactory(ListView<StreamSource> availableStreamsListView) {
        availableStreamsListView.setCellFactory((ListView<StreamSource> param) -> new ListCell<>() {
            private CheckBox checkBox;

            @Override
            public void updateItem(StreamSource streamSource, boolean empty) {
                super.updateItem(streamSource, empty);
                if (empty || streamSource == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    getCheckBox().setSelected(true);    // import all streams per default
                    setGraphic(getCheckBox());
                    setText(streamSource.getName());
                    setTooltip(new Tooltip(streamSource.getDescription()));
                }
            }

            /**
             * Returns a CheckBox object.
             * <p>
             * The method checks if the "checkBox" instance variable is null. If it is, a new CheckBox object is created.
             * The method also adds a listener to the selectedProperty of the CheckBox object. This listener updates the
             * dataModel's filters based on the selection state of the CheckBox.
             *
             * @return a CheckBox object.
             */
            private CheckBox getCheckBox() {
                if (checkBox == null) {
                    checkBox = new CheckBox();
                    checkBox.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                        List<StreamSource> selectedStreamSources = fritzBoxConnection.getSelectedStreamSources();
                        if (getItem() != null) {
                            if (Boolean.TRUE.equals(isNowSelected)) {
                                selectedStreamSources.add(getItem());
                            } else {
                                selectedStreamSources.remove(getItem());
                            }
                        }
                        log.debug("Currently selected Filters: {}", selectedStreamSources);
                    });
                }
                return checkBox;
            }
        });
    }

}
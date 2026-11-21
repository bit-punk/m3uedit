package org.bitpunk.m3uedit.plugins.filter.channelname;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import org.bitpunk.m3uedit.controller.AbstractDialogController;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.plugins.filter.channelname.model.ChannelNameFilterConfig;

import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;
import java.util.ResourceBundle;

@Slf4j
public class ChannelNameFilterConfigDialogController extends AbstractDialogController<ChannelNameFilterConfig> implements Initializable {
    public static final String FXML_PLUGIN_FILTER_CONFIG = "plugin/filter/channelname/ChannelNameFilterConfigDialog.fxml";

    @FXML
    @Getter
    private ListView<String> patternsListView;

    @FXML
    @Getter
    private Button moveUpButton;

    @FXML
    @Getter
    private Button moveDownButton;

    @FXML
    @Getter
    private Button removePatternButton;

    @FXML
    @Getter
    private TextField newPatternTextField;

    @FXML
    @Getter
    private Button addPatternButton;

    @FXML
    @Getter
    private ButtonType okButtonType;

    @Getter
    private ChannelNameFilter filter;

    @Getter
    private ChannelNameFilterConfig workingConfig;

    public ChannelNameFilterConfigDialogController() {
        this(new ChannelNameFilter());
    }

    public ChannelNameFilterConfigDialogController(ChannelNameFilter filter) {
        super(FXML_PLUGIN_FILTER_CONFIG, Messages.get("filter.channelName.config.title"));
        this.filter = filter;
        setResultConverter(createResultConverter());
        initData(filter);
    }

    private Callback<ButtonType, ChannelNameFilterConfig> createResultConverter() {
        return buttonType -> {
            if (Objects.equals(ButtonBar.ButtonData.OK_DONE, buttonType.getButtonData())) {
                log.debug("OK pressed in ChannelNameFilterConfigDialog");
                saveWorkingState();
                applyAndPersistConfig();
                return workingConfig;
            }
            return null;
        };
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupListView();
        setupButtons();
        setupBindings();
    }

    private void setupListView() {
        patternsListView.setEditable(true);
        patternsListView.setCellFactory(TextFieldListCell.forListView());
        patternsListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        patternsListView.setOnKeyPressed(this::handleKeyPressed);
    }

    private void setupButtons() {
        addPatternButton.setOnAction(event -> addPattern());
        newPatternTextField.setOnAction(event -> addPattern());
        removePatternButton.setOnAction(event -> removeSelectedPattern());
        moveUpButton.setOnAction(event -> moveUp());
        moveDownButton.setOnAction(event -> moveDown());
    }

    private void setupBindings() {
        addPatternButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> newPatternTextField.getText() == null || newPatternTextField.getText().trim().isEmpty(),
                        newPatternTextField.textProperty()
                )
        );

        removePatternButton.disableProperty().bind(
                patternsListView.getSelectionModel().selectedItemProperty().isNull()
        );

        moveUpButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> patternsListView.getSelectionModel().getSelectedIndex() <= 0,
                        patternsListView.getSelectionModel().selectedIndexProperty()
                )
        );

        moveDownButton.disableProperty().bind(
                Bindings.createBooleanBinding(() -> {
                    int selectedIndex = patternsListView.getSelectionModel().getSelectedIndex();
                    return selectedIndex < 0 || selectedIndex >= patternsListView.getItems().size() - 1;
                }, patternsListView.getSelectionModel().selectedIndexProperty(), patternsListView.getItems())
        );
    }

    public void initData(ChannelNameFilter filter) {
        this.filter = filter;
        ChannelNameFilterConfig originalConfig = filter != null ? filter.getConfig() : null;
        this.workingConfig = copyConfig(originalConfig);

        patternsListView.getItems().clear();
        if (workingConfig.getExcludeRegexes() != null) {
            patternsListView.getItems().addAll(workingConfig.getExcludeRegexes());
        }
    }

    public void addPattern() {
        String text = newPatternTextField.getText();
        if (text != null && !text.trim().isEmpty()) {
            String trimmed = text.trim();
            patternsListView.getItems().add(trimmed);
            newPatternTextField.clear();
            patternsListView.getSelectionModel().select(trimmed);
            patternsListView.scrollTo(trimmed);
        }
    }

    public void removeSelectedPattern() {
        int selectedIndex = patternsListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            patternsListView.getItems().remove(selectedIndex);
            int newSize = patternsListView.getItems().size();
            if (newSize > 0) {
                int newIndex = Math.min(selectedIndex, newSize - 1);
                patternsListView.getSelectionModel().select(newIndex);
            }
        }
    }

    public void moveUp() {
        int index = patternsListView.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            ObservableList<String> items = patternsListView.getItems();
            String item = items.remove(index);
            items.add(index - 1, item);
            patternsListView.getSelectionModel().select(index - 1);
        }
    }

    public void moveDown() {
        int index = patternsListView.getSelectionModel().getSelectedIndex();
        ObservableList<String> items = patternsListView.getItems();
        if (index >= 0 && index < items.size() - 1) {
            String item = items.remove(index);
            items.add(index + 1, item);
            patternsListView.getSelectionModel().select(index + 1);
        }
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            removeSelectedPattern();
            event.consume();
        }
    }

    public void saveWorkingState() {
        workingConfig.setExcludeRegexes(new ArrayList<>(patternsListView.getItems()));
    }

    public void applyAndPersistConfig() {
        if (filter != null) {
            filter.setConfig(workingConfig);
            if (filter.getConfigManager() != null) {
                filter.getConfigManager().saveConfig(workingConfig);
            }
        }
    }

    private ChannelNameFilterConfig copyConfig(ChannelNameFilterConfig original) {
        if (original == null) {
            return ChannelNameFilterConfig.builder().build();
        }
        return ChannelNameFilterConfig.builder()
                .excludeRegexes(original.getExcludeRegexes() != null ? new ArrayList<>(original.getExcludeRegexes()) : new ArrayList<>())
                .build();
    }
}

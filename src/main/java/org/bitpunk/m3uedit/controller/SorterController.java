package org.bitpunk.m3uedit.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class SorterController extends AbstractController {
    public static final String FXML_SORTER = "fxml/Sorter.fxml";
    private static final Logger log = LoggerFactory.getLogger(SorterController.class);
    @FXML
    public ListView<Sorter> availableSortersListView;
    private DataModel dataModel;

    public SorterController() {
        super(FXML_SORTER);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    private void initAvailableSorterListView() {
        setCellFactory(availableSortersListView);
        List<Sorter> sorterPlugins = SorterPluginService.getInstance().getAllPlugins();
        log.debug("sorter-plugins: {}", sorterPlugins);
        availableSortersListView.getItems().addAll(sorterPlugins);
    }

    private void setCellFactory(ListView<Sorter> availableSortersListView) {
        availableSortersListView.setCellFactory((ListView<Sorter> param) -> new ListCell<>() {
            private CheckBox checkBox;

            @Override
            public void updateItem(Sorter sorter, boolean empty) {
                super.updateItem(sorter, empty);
                if (!(empty || sorter == null)) {
                    getCheckBox().setSelected(dataModel.getSorters().stream().anyMatch(s -> s.getName().equals(sorter.getName())));
                    setGraphic(getCheckBox());
                    setText(sorter.getName());
                    setTooltip(new Tooltip(sorter.getDescription()));
                } else {
                    setGraphic(null);
                    setText(null);
                }
            }

            private CheckBox getCheckBox() {
                if (checkBox == null) {
                    checkBox = new CheckBox();
                    checkBox.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                        if (getItem() != null) {
                            if (isNowSelected) {
                                dataModel.getSorters().add(getItem());
                            } else {
                                dataModel.getSorters().remove(getItem());
                            }
                        }
                        log.debug("Currently selected Sorters: {}", dataModel.getSorters());
                    });
                }
                return checkBox;
            }

        });
    }

    @Override
    public void initModel(DataModel dataModel) {
        if (this.dataModel != null) {
            throw new IllegalStateException("Model can only be initialized once");
        }
        this.dataModel = dataModel;
        initAvailableSorterListView();
    }
}
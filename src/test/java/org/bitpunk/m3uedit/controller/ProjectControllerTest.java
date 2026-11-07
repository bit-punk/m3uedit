package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @Test
    void testProjectControllerInitialization() {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        ProjectController projectController = new ProjectController(dataModel);

        assertThat(projectController.inputController).isNotNull();
        assertThat(projectController.rawEntriesController).isNotNull();
        assertThat(projectController.filterController).isNotNull();
        assertThat(projectController.filteredEntriesController).isNotNull();
        assertThat(projectController.sorterController).isNotNull();
        assertThat(projectController.entryListController).isNotNull();
        assertThat(projectController.outputController).isNotNull();
        assertThat(projectController.getDataModel()).isSameAs(dataModel);

        assertThat(projectController.inputTab.getContent()).isSameAs(projectController.inputController);
        assertThat(projectController.rawEntriesTab.getContent()).isSameAs(projectController.rawEntriesController);
        assertThat(projectController.filterTab.getContent()).isSameAs(projectController.filterController);
        assertThat(projectController.filteredEntriesTab.getContent()).isSameAs(projectController.filteredEntriesController);
        assertThat(projectController.sorterTab.getContent()).isSameAs(projectController.sorterController);
        assertThat(projectController.entryTab.getContent()).isSameAs(projectController.entryListController);
        assertThat(projectController.outputTab.getContent()).isSameAs(projectController.outputController);
    }

    @Test
    void testStatusLabelUpdates() {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        // compute synchronously
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        ProjectController projectController = new ProjectController(dataModel);

        Label label = new Label();
        projectController.setStatusLabel(label);

        assertThat(projectController.getStatusLabel()).isSameAs(label);
        assertThat(label.getText()).isEqualTo("0 entries loaded");

        Entry entry1 = new Entry();
        entry1.setName("Channel 1");
        dataModel.getRawEntryList().add(entry1);

        // Default selected tab is inputTab, which falls back to sortedEntryList (cascaded 1 entry)
        assertThat(label.getText()).isEqualTo("1 entries loaded");

        // Select raw entries tab
        projectController.rawEntriesTab.getTabPane().getSelectionModel().select(projectController.rawEntriesTab);
        assertThat(label.getText()).isEqualTo("1 entries loaded");

        // Select filtered entries tab
        projectController.filteredEntriesTab.getTabPane().getSelectionModel().select(projectController.filteredEntriesTab);
        assertThat(label.getText()).isEqualTo("1 entries loaded");

        // Select sorter tab
        projectController.sorterTab.getTabPane().getSelectionModel().select(projectController.sorterTab);
        assertThat(label.getText()).isEqualTo("1 entries loaded");
    }

    @Test
    void testStatusLabelShowsLoadingIndicatorWhileBusy() {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        List<Runnable> backgroundTasks = new ArrayList<>();
        dataModel.setBackgroundExecutor(backgroundTasks::add);
        dataModel.setFxExecutor(Runnable::run);
        ProjectController projectController = new ProjectController(dataModel);
        Label label = new Label();
        projectController.setStatusLabel(label);

        dataModel.reloadInputs();
        assertThat(label.getText()).isEqualTo(ProjectController.LOADING_MESSAGE);
        assertThat(label.getGraphic()).isInstanceOf(ProgressIndicator.class);

        backgroundTasks.forEach(Runnable::run);
        assertThat(label.getText()).isEqualTo("0 entries loaded");
        assertThat(label.getGraphic()).isNull();
    }

    @Test
    void testTogglingAPluginRecomputesOnce() {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        List<Runnable> backgroundTasks = new ArrayList<>();
        dataModel.setBackgroundExecutor(backgroundTasks::add);
        dataModel.setFxExecutor(Runnable::run);
        new ProjectController(dataModel);

        dataModel.getFilters().getFirst().getEnabled().set(true);
        assertThat(backgroundTasks).hasSize(1);

        dataModel.getSorters().getFirst().getEnabled().set(true);
        assertThat(backgroundTasks).hasSize(2);
    }
}

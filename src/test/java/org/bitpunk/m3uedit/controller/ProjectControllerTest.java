package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.output.file.FileOutput;
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

    private static DataModel synchronousDataModel() {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        return dataModel;
    }

    @Test
    void testProjectControllerInitialization() {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        ProjectController projectController = new ProjectController(dataModel);

        assertThat(projectController.pipelineController).isNotNull();
        assertThat(projectController.summaryView).isNotNull();
        assertThat(projectController.entryListController).isNotNull();
        assertThat(projectController.diffController).isNotNull();
        assertThat(projectController.getDataModel()).isSameAs(dataModel);
        assertThat(projectController.playlistTab.getContent()).isSameAs(projectController.entryListController);
        assertThat(projectController.diffTab.getContent()).isSameAs(projectController.diffController);
        // each filter and sorter of the project is shown in the pipeline
        assertThat(projectController.pipelineController.getFilterStage().rowOf(dataModel.getFilters().getFirst())).isPresent();
        assertThat(projectController.pipelineController.getSorterStage().rowOf(dataModel.getSorters().getFirst())).isPresent();
    }

    @Test
    void testStatusLabelShowsTheSizeOfThePlaylist() {
        DataModel dataModel = synchronousDataModel();
        ProjectController projectController = new ProjectController(dataModel);

        Label label = new Label();
        projectController.setStatusLabel(label);

        assertThat(projectController.getStatusLabel()).isSameAs(label);
        assertThat(label.getText()).isEqualTo("0 entries in the playlist");

        Entry entry1 = new Entry();
        entry1.setName("Channel 1");
        dataModel.getRawEntryList().add(entry1);

        assertThat(label.getText()).isEqualTo("1 entries in the playlist");
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
        assertThat(label.getText()).isEqualTo(ProjectController.loadingMessage());
        assertThat(label.getGraphic()).isInstanceOf(ProgressIndicator.class);

        backgroundTasks.forEach(Runnable::run);
        assertThat(label.getText()).isEqualTo("0 entries in the playlist");
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

    @Test
    void testSelectingAStepShowsTheDiffAndAnOutputShowsThePlaylist() {
        DataModel dataModel = synchronousDataModel();
        FileOutput output = new FileOutput();
        dataModel.getOutputs().add(output);
        ProjectController projectController = new ProjectController(dataModel);
        var tabs = projectController.resultTabPane.getSelectionModel();

        projectController.pipelineController.getSelectedStep().set(dataModel.getFilters().getFirst());
        assertThat(tabs.getSelectedItem()).isSameAs(projectController.diffTab);
        assertThat(projectController.getSelectedStep()).isSameAs(dataModel.getFilters().getFirst());

        projectController.pipelineController.getSelectedStep().set(output);
        assertThat(tabs.getSelectedItem()).isSameAs(projectController.playlistTab);
    }
}

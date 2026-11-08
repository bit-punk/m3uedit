package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import org.bitpunk.m3uedit.controller.TestPlugins.NameSorter;
import org.bitpunk.m3uedit.controller.TestPlugins.TestFilter;
import org.bitpunk.m3uedit.controller.TestPlugins.TestInput;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bitpunk.m3uedit.controller.TestPlugins.entry;

class PipelineControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private DataModel dataModel;
    private TestFilter hdFilter;
    private TestFilter shopFilter;
    private NameSorter sorter;
    private PipelineController pipeline;

    @BeforeEach
    void setUp() {
        dataModel = new DataModel("test");
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        hdFilter = new TestFilter("HD Filter", entry -> !entry.getName().endsWith("HD"));
        shopFilter = new TestFilter("Shop Filter", entry -> entry.getName().startsWith("QVC"));
        sorter = new NameSorter();
        dataModel.getFilters().addAll(hdFilter, shopFilter);
        dataModel.getSorters().add(sorter);
        pipeline = new PipelineController();
        pipeline.initModel(dataModel);
        dataModel.getInputs().add(new TestInput("FritzBox", List.of(entry("ZDF HD"), entry("QVC"), entry("Das Erste HD"), entry("arte"))));
    }

    private static List<String> texts(Node root) {
        List<String> texts = new ArrayList<>();
        if (root instanceof Label label && label.getText() != null && label.isVisible()) {
            texts.add(label.getText());
        }
        if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> texts.addAll(texts(child)));
        }
        return texts;
    }

    @Test
    void connectorsShowTheNumberOfEntriesPassedOn() {
        hdFilter.getEnabled().set(true);

        assertThat(texts(pipeline)).contains("4 entries loaded", "2 left after filtering", "2 entries in the playlist");
    }

    @Test
    void badgesShowTheEffectOfEachStep() {
        hdFilter.getEnabled().set(true);
        sorter.getEnabled().set(true);

        assertThat(texts(pipeline.getInputStage().rowOf(dataModel.getInputs().getFirst()).orElseThrow())).contains("+4");
        assertThat(texts(pipeline.getFilterStage().rowOf(hdFilter).orElseThrow())).contains("−2");
        // a disabled filter shows no badge
        assertThat(texts(pipeline.getFilterStage().rowOf(shopFilter).orElseThrow())).doesNotContain("−1", "±0");
        assertThat(texts(pipeline.getSorterStage().rowOf(sorter).orElseThrow())).contains("⇅ 1");
    }

    @Test
    void reorderingFiltersRecomputesTheEntries() {
        hdFilter.getEnabled().set(true);
        shopFilter.getEnabled().set(true);
        assertThat(dataModel.getPipelineResult().step(hdFilter).orElseThrow().input()).hasSize(4);

        dataModel.getFilters().setAll(shopFilter, hdFilter);

        assertThat(dataModel.getPipelineResult().step(shopFilter).orElseThrow().input()).hasSize(4);
        assertThat(dataModel.getPipelineResult().step(hdFilter).orElseThrow().input()).hasSize(3);
    }

    @Test
    void configuringAFilterRecomputesTheEntries() {
        hdFilter.getEnabled().set(true);
        dataModel.getSortedEntryList().clear();

        Node row = pipeline.getFilterStage().rowOf(hdFilter).orElseThrow();
        javafx.event.Event.fireEvent(row, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 2,
                false, false, false, false, false, false, false, false, false, false, null));

        assertThat(hdFilter.configured).hasValue(1);
        assertThat(dataModel.getSortedEntryList()).extracting(Entry::getName).containsExactly("ZDF HD", "Das Erste HD");
    }

    @Test
    void removingTheSelectedInputClearsTheSelection() {
        TestInput input = (TestInput) dataModel.getInputs().getFirst();
        pipeline.getSelectedStep().set(input);

        dataModel.getInputs().remove(input);

        assertThat(pipeline.getSelectedStep().get()).isNull();
        assertThat(dataModel.getRawEntryList()).isEmpty();
    }

    @Test
    void writeButtonIsDisabledWhileSaving() {
        List<Runnable> background = new ArrayList<>();
        dataModel.setBackgroundExecutor(background::add);

        pipeline.getWriteButton().fire();

        assertThat(pipeline.getWriteButton().isDisabled()).isTrue();
        background.forEach(Runnable::run);
        assertThat(pipeline.getWriteButton().isDisabled()).isFalse();
    }

    @Test
    void collapsedStagesShowTheirStatus() {
        hdFilter.getEnabled().set(true);
        sorter.getEnabled().set(true);
        List.of(pipeline.getInputStage(), pipeline.getFilterStage(), pipeline.getSorterStage(), pipeline.getOutputStage())
                .forEach(stage -> stage.setCollapsed(true));

        assertThat(pipeline.getInputStage().getHeaderText()).isEqualTo("1 input, 4 entries");
        assertThat(pipeline.getFilterStage().getHeaderText()).isEqualTo("1 of 2 enabled, −2 entries");
        assertThat(pipeline.getSorterStage().getHeaderText()).isEqualTo("1 of 1 enabled, ⇅ 1 entry");
        assertThat(pipeline.getOutputStage().getHeaderText()).isEqualTo("no output");

        // the status follows the enabled filters
        shopFilter.getEnabled().set(true);
        hdFilter.getEnabled().set(false);

        assertThat(pipeline.getFilterStage().getHeaderText()).isEqualTo("1 of 2 enabled, −1 entry");
    }
}

package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.assertj.core.data.Offset;
import org.bitpunk.m3uedit.controller.TestPlugins.NameSorter;
import org.bitpunk.m3uedit.controller.TestPlugins.TestEnricher;
import org.bitpunk.m3uedit.controller.TestPlugins.TestFilter;
import org.bitpunk.m3uedit.controller.TestPlugins.TestInput;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.output.file.FileOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    void enricherStageIsOnlyShownIfThereAreEnrichers() {
        assertThat(pipeline.getEnricherStage().isVisible()).isFalse();
        assertThat(pipeline.getEnricherStage().isManaged()).isFalse();

        dataModel.getEnrichers().add(new TestEnricher());

        assertThat(pipeline.getEnricherStage().isVisible()).isTrue();
    }

    @Test
    void enricherBadgeShowsTheNumberOfChangedEntries() {
        TestEnricher enricher = new TestEnricher();
        dataModel.getEnrichers().add(enricher);
        PipelineController controller = new PipelineController();
        controller.initModel(dataModel);

        enricher.getEnabled().set(true);

        assertThat(texts(controller.getEnricherStage().rowOf(enricher).orElseThrow())).contains("✎ 4");
        assertThat(dataModel.getSortedEntryList()).extracting(Entry::getTvgId).contains("ZDF HD", "ARTE");
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
        Event.fireEvent(row, new MouseEvent(MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, MouseButton.PRIMARY, 2,
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
    void filtersSortersAndEnrichersCanBeEnabledAllAtOnce() {
        assertThat(pipeline.getFilterStage().getEnableAllCheckBox()).isNotNull();
        assertThat(pipeline.getEnricherStage().getEnableAllCheckBox()).isNotNull();
        // only one sorter is enabled at a time
        assertThat(pipeline.getSorterStage().getEnableAllCheckBox()).isNull();
        // inputs and outputs are always used
        assertThat(pipeline.getInputStage().getEnableAllCheckBox()).isNull();
        assertThat(pipeline.getOutputStage().getEnableAllCheckBox()).isNull();
    }

    @Test
    void enableAllCheckBoxIsAlignedWithTheCheckBoxesOfTheRows() {
        dataModel.getEnrichers().add(new TestPlugins.TestEnricher());
        // styled like in a project: its style class defines the colours (e.g. of the borders)
        VBox root = new VBox(pipeline);
        root.getStyleClass().add("project");
        Scene scene = new Scene(root, 420, 1200);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getClassLoader().getResource("fxml/css/project.css")).toExternalForm());
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        for (var stage : List.of(pipeline.getFilterStage(), pipeline.getEnricherStage())) {
            Node headerBox = stage.getEnableAllCheckBox().lookup(".box");
            Node rowBox = stage.lookupAll(".plugin-row").iterator().next().lookup(".check-box").lookup(".box");
            assertThat(headerBox.localToScene(headerBox.getBoundsInLocal()).getMinX())
                    .as("header checkbox of %s", stage.getHeaderText())
                    .isCloseTo(rowBox.localToScene(rowBox.getBoundsInLocal()).getMinX(), Offset.offset(0.5));
        }
    }

    @Test
    void filtersAndSortersCannotBeReorderedButEnrichersCan() {
        dataModel.getEnrichers().add(new TestPlugins.TestEnricher());

        for (var stage : List.of(pipeline.getFilterStage(), pipeline.getSorterStage())) {
            Node row = stage.lookupAll(".plugin-row").iterator().next();
            assertThat(row.lookup(".grip").isVisible()).isFalse();
            assertThat(row.lookupAll(".button").stream().map(button -> ((javafx.scene.control.Button) button).getTooltip())
                    .filter(java.util.Objects::nonNull).map(javafx.scene.control.Tooltip::getText))
                    .doesNotContain("Move up", "Move down");
        }
        Node enricherRow = pipeline.getEnricherStage().lookupAll(".plugin-row").iterator().next();
        assertThat(enricherRow.lookup(".grip").isVisible()).isTrue();
    }

    @Test
    void enablingASorterDisablesTheOtherOnes() {
        NameSorter other = new NameSorter();
        dataModel.getSorters().add(other);
        sorter.getEnabled().set(true);

        other.getEnabled().set(true);

        assertThat(sorter.getEnabled().get()).isFalse();
        assertThat(pipeline.getSorterStage().getHeaderText()).isEqualTo("one at a time");
    }

    @Test
    void enableAllCheckBoxShowsWhetherAllSomeOrNoneAreEnabled() {
        CheckBox all = pipeline.getFilterStage().getEnableAllCheckBox();
        assertThat(all.isSelected()).isFalse();
        assertThat(all.isIndeterminate()).isFalse();
        assertThat(all.getTooltip().getText()).isEqualTo("Enable all filters");

        hdFilter.getEnabled().set(true);
        assertThat(all.isIndeterminate()).isTrue();

        shopFilter.getEnabled().set(true);
        assertThat(all.isSelected()).isTrue();
        assertThat(all.isIndeterminate()).isFalse();
        assertThat(all.getTooltip().getText()).isEqualTo("Disable all filters");
    }

    @Test
    void enableAllEnablesAllIfSomeAreDisabledAndDisablesAllOtherwise() {
        CheckBox all = pipeline.getFilterStage().getEnableAllCheckBox();
        hdFilter.getEnabled().set(true);

        all.fire();
        assertThat(dataModel.getFilters()).allMatch(filter -> filter.getEnabled().get());
        assertThat(all.isSelected()).isTrue();
        assertThat(texts(pipeline)).contains("2 left after filtering");

        all.fire();
        assertThat(dataModel.getFilters()).noneMatch(filter -> filter.getEnabled().get());
        assertThat(all.isSelected()).isFalse();
        assertThat(texts(pipeline)).contains("4 left after filtering");
    }

    @Test
    void enablingAllRecomputesTheEntriesOnce() {
        List<Runnable> background = new ArrayList<>();
        dataModel.setBackgroundExecutor(background::add);

        pipeline.getFilterStage().getEnableAllCheckBox().fire();

        assertThat(background).hasSize(1);
        background.forEach(Runnable::run);
        // the HD filter keeps the HD channels only, the shop filter removes QVC
        assertThat(dataModel.getFilteredEntryList()).extracting(Entry::getName).containsExactly("ZDF HD", "Das Erste HD");
    }

    @Test
    void writeButtonIsOnlyEnabledWithAnOutputAndEntries() {
        assertThat(dataModel.getSortedEntryList()).isNotEmpty();
        assertThat(pipeline.getWriteButton().isDisabled()).as("no output").isTrue();
        assertThat(pipeline.getWriteHint().isVisible()).isTrue();
        assertThat(pipeline.getWriteHint().getText()).isEqualTo("Add an output to write the playlist");

        dataModel.getOutputs().add(new FileOutput());
        assertThat(pipeline.getWriteButton().isDisabled()).isFalse();
        assertThat(pipeline.getWriteHint().isVisible()).isFalse();

        dataModel.getInputs().clear();
        assertThat(dataModel.getSortedEntryList()).isEmpty();
        assertThat(pipeline.getWriteButton().isDisabled()).as("no entries").isTrue();
        assertThat(pipeline.getWriteHint().getText()).isEqualTo("The playlist is empty, there is nothing to write");

        dataModel.getOutputs().clear();
        assertThat(pipeline.getWriteHint().getText()).as("the output is missing first").isEqualTo("Add an output to write the playlist");
    }

    @Test
    void writeButtonIsDisabledWhileSaving() {
        List<Runnable> background = new ArrayList<>();
        dataModel.setBackgroundExecutor(background::add);
        dataModel.getOutputs().add(new FileOutput());
        assertThat(pipeline.getWriteButton().isDisabled()).isFalse();

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
        assertThat(pipeline.getSorterStage().getHeaderText()).isEqualTo("Name Sorter, ⇅ 1 entry");
        assertThat(pipeline.getOutputStage().getHeaderText()).isEqualTo("no output");

        // the status follows the enabled filters
        shopFilter.getEnabled().set(true);
        hdFilter.getEnabled().set(false);

        assertThat(pipeline.getFilterStage().getHeaderText()).isEqualTo("1 of 2 enabled, −1 entry");
    }
}

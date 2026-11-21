package org.bitpunk.m3uedit.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Undo and redo of the changes of the playlist made by hand.
 */
class DataModelHistoryTest {
    private DataModel dataModel;

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name);
        return entry;
    }

    private static class FixedInput implements Input {
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);

        @Override
        public List<Entry> read() {
            return List.of(entry("A"), entry("B"), entry("C"));
        }

        @Override
        public String getSource() {
            return "fixed";
        }

        @Override
        public void configure() {
            // nothing to configure
        }

        @Override
        public String getName() {
            return "Fixed";
        }

        @Override
        public String getDescription() {
            return "A, B, C";
        }

        @Override
        public BooleanProperty getEnabled() {
            return enabled;
        }
    }

    @BeforeEach
    void setUp() {
        dataModel = new DataModel("history");
        dataModel.setBackgroundExecutor(Runnable::run);
        dataModel.setFxExecutor(Runnable::run);
        dataModel.getInputs().add(new FixedInput());
    }

    private List<String> playlist() {
        return dataModel.getSortedEntryList().stream().map(Entry::getName).toList();
    }

    private Entry named(String name) {
        return dataModel.getSortedEntryList().stream().filter(entry -> entry.getName().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    void testNothingToUndoAfterOpening() {
        assertThat(playlist()).containsExactly("A", "B", "C");
        assertThat(dataModel.canUndoProperty().get()).isFalse();
        assertThat(dataModel.canRedoProperty().get()).isFalse();
    }

    @Test
    void testEachKindOfChangeIsUndoneAndRedone() {
        dataModel.addEntry(entry("D"));
        dataModel.removeEntries(List.of(named("A")));
        dataModel.moveEntries(List.of(named("C")), 0);
        dataModel.editEntry(named("B"), entry("B2"));
        assertThat(playlist()).containsExactly("C", "B2", "D");

        dataModel.undo();
        assertThat(playlist()).as("edit undone").containsExactly("C", "B", "D");
        dataModel.undo();
        assertThat(playlist()).as("move undone").containsExactly("B", "C", "D");
        dataModel.undo();
        assertThat(playlist()).as("remove undone").containsExactly("A", "B", "C", "D");
        dataModel.undo();
        assertThat(playlist()).as("add undone").containsExactly("A", "B", "C");
        assertThat(dataModel.canUndoProperty().get()).isFalse();

        dataModel.redo();
        dataModel.redo();
        assertThat(playlist()).containsExactly("B", "C", "D");
        dataModel.redo();
        dataModel.redo();
        assertThat(playlist()).containsExactly("C", "B2", "D");
        assertThat(dataModel.canRedoProperty().get()).isFalse();
    }

    @Test
    void testEntriesInsertedAtAPositionAreOneStep() {
        dataModel.insertEntries(List.of(entry("X"), entry("Y")), 1);
        assertThat(playlist()).containsExactly("A", "X", "Y", "B", "C");

        dataModel.undo();

        assertThat(playlist()).containsExactly("A", "B", "C");
        assertThat(dataModel.canUndoProperty().get()).isFalse();
        dataModel.redo();
        assertThat(playlist()).containsExactly("A", "X", "Y", "B", "C");
    }

    @Test
    void testRemovingSeveralEntriesIsOneStep() {
        dataModel.removeEntries(List.of(named("A"), named("C")));

        dataModel.undo();

        assertThat(playlist()).containsExactly("A", "B", "C");
    }

    @Test
    void testResettingTheManualChangesCanBeUndone() {
        dataModel.addEntry(entry("D"));
        dataModel.resetManualChanges();
        assertThat(playlist()).containsExactly("A", "B", "C");

        dataModel.undo();

        assertThat(playlist()).containsExactly("A", "B", "C", "D");
        assertThat(dataModel.getManualChanges()).hasSize(1);
    }

    @Test
    void testANewChangeClearsTheRedoHistory() {
        dataModel.addEntry(entry("D"));
        dataModel.undo();
        assertThat(dataModel.canRedoProperty().get()).isTrue();

        dataModel.addEntry(entry("E"));

        assertThat(dataModel.canRedoProperty().get()).isFalse();
        assertThat(playlist()).containsExactly("A", "B", "C", "E");
    }

    @Test
    void testUndoMarksTheProjectAsChanged() {
        dataModel.addEntry(entry("D"));
        dataModel.setChanged(false);

        dataModel.undo();

        assertThat(dataModel.isChanged()).isTrue();
    }

    @Test
    void testTheChangesStayUndoneWhenThePipelineIsComputedAgain() {
        dataModel.addEntry(entry("D"));
        dataModel.undo();

        dataModel.reloadInputs();

        assertThat(playlist()).containsExactly("A", "B", "C");
        assertThat(dataModel.canRedoProperty().get()).as("still available").isTrue();
    }
}

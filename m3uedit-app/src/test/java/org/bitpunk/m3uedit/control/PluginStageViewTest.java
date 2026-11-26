package org.bitpunk.m3uedit.control;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginStageViewTest {

    /**
     * A plugin offered by the add-button, named by itself (without {@code @PluginType}).
     */
    private static PluginDescriptor<TestPlugin> addable(String name) {
        return new PluginDescriptor<>(name, TestPlugin.class, new ServiceLoader.Provider<>() {
            @Override
            public Class<? extends TestPlugin> type() {
                return TestPlugin.class;
            }

            @Override
            public TestPlugin get() {
                return new TestPlugin(name);
            }
        });
    }

    private static class TestPlugin implements Plugin {
        private final String name;

        int configured;

        TestPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name + " description";
        }

        @Override
        public boolean isConfigurable() {
            return true;
        }

        @Override
        public boolean configure(Window owner) {
            configured++;
            return true;
        }
    }

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private TestPlugin first;
    private TestPlugin second;
    private TestPlugin third;
    private ObservableList<TestPlugin> plugins;
    private ObjectProperty<Plugin> selection;

    @BeforeEach
    void setUp() {
        first = new TestPlugin("first");
        second = new TestPlugin("second");
        third = new TestPlugin("third");
        plugins = FXCollections.observableArrayList(first, second, third);
        selection = new SimpleObjectProperty<>();
    }

    private PluginStageView<TestPlugin> orderableStage() {
        return new PluginStageView<>("Filters", "hint", plugins, selection, true, Plugin::getDescription);
    }

    private static MouseEvent click(int count) {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, count,
                false, false, false, false, false, false, false, false, false, false, null);
    }

    private static <N extends Node> List<N> find(Node root, Class<N> type) {
        List<N> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> found.addAll(find(child, type)));
        }
        return found;
    }

    private static Button button(Node row, String text) {
        return find(row, Button.class).stream().filter(b -> text.equals(b.getAccessibleText())).findFirst().orElseThrow();
    }

    private static HBox row(PluginStageView<TestPlugin> stage, TestPlugin plugin) {
        return stage.rowOf(plugin).orElseThrow();
    }

    @Test
    void clickingARowSelectsAndDeselectsItsPlugin() {
        PluginStageView<TestPlugin> stage = orderableStage();

        Event.fireEvent(row(stage, second), click(1));
        assertThat(selection.get()).isSameAs(second);

        Event.fireEvent(row(stage, second), click(1));
        assertThat(selection.get()).isNull();
    }

    @Test
    void aChangedConfigurationIsReported() {
        PluginStageView<TestPlugin> stage = orderableStage();
        AtomicInteger changed = new AtomicInteger();
        stage.setOnConfigurationChanged(plugin -> changed.incrementAndGet());
        // the state changes with each configuration
        stage.setPersistedState(plugin -> "configured " + plugin.configured);

        Event.fireEvent(row(stage, first), click(2));

        assertThat(changed).hasValue(1);
    }

    @Test
    void anUnchangedConfigurationIsNotReported() {
        PluginStageView<TestPlugin> stage = orderableStage();
        AtomicInteger changed = new AtomicInteger();
        stage.setOnConfigurationChanged(plugin -> changed.incrementAndGet());
        // e.g. the dialog has been cancelled
        stage.setPersistedState(plugin -> "unchanged");

        Event.fireEvent(row(stage, first), click(2));

        assertThat(first.configured).isEqualTo(1);
        assertThat(changed).hasValue(0);
    }

    @Test
    void doubleClickConfiguresThePlugin() {
        PluginStageView<TestPlugin> stage = orderableStage();
        AtomicInteger configured = new AtomicInteger();
        stage.setOnConfigured(plugin -> configured.incrementAndGet());

        Event.fireEvent(row(stage, first), click(2));

        assertThat(first.configured).isEqualTo(1);
        assertThat(configured).hasValue(1);
        assertThat(selection.get()).isSameAs(first);
    }

    @Test
    void checkBoxEnablesThePlugin() {
        PluginStageView<TestPlugin> stage = orderableStage();
        CheckBox checkBox = find(row(stage, third), CheckBox.class).getFirst();

        checkBox.fire();
        assertThat(PluginStates.enabled(third).get()).isTrue();

        PluginStates.enabled(third).set(false);
        assertThat(checkBox.isSelected()).isFalse();
    }

    @Test
    void movingAPluginChangesTheListOnce() {
        PluginStageView<TestPlugin> stage = orderableStage();
        AtomicInteger changes = new AtomicInteger();
        plugins.addListener((ListChangeListener<TestPlugin>) change -> changes.incrementAndGet());

        button(row(stage, third), "Move up").fire();

        assertThat(plugins).containsExactly(first, third, second);
        assertThat(changes).hasValue(1);
        assertThat(button(row(stage, first), "Move up").isDisabled()).isTrue();
        assertThat(button(row(stage, second), "Move down").isDisabled()).isTrue();
    }

    @Test
    void pluginsCanOnlyBeRemovedWithAnAddAction() {
        PluginStageView<TestPlugin> stage = new PluginStageView<>("Inputs", "hint", plugins, selection, false,
                plugin -> "source");
        assertThat(find(row(stage, first), Button.class)).noneMatch(b -> "Remove".equals(b.getAccessibleText()));
        assertThat(find(row(stage, first), CheckBox.class)).isEmpty();

        List<String> added = new ArrayList<>();
        stage.setAddAction(List.of(addable("File Input"), addable("URL Input")),
                plugin -> added.add(plugin.create().getName()));
        stage.setRemoveConfirmation(plugin -> true);
        selection.set(first);

        button(row(stage, first), "Remove").fire();

        assertThat(plugins).containsExactly(second, third);
        assertThat(selection.get()).isNull();
        assertThat(stage.rowOf(first)).isEmpty();

        assertThat(stage.getAddButton().getItems()).extracting(MenuItem::getText)
                .containsExactly("File Input", "URL Input");
        stage.getAddButton().getItems().get(1).fire();
        assertThat(added).containsExactly("URL Input");
    }

    @Test
    void removingCanBeCancelled() {
        PluginStageView<TestPlugin> stage = new PluginStageView<>("Inputs", "hint", plugins, selection, false,
                plugin -> "source");
        stage.setAddAction(List.of(), plugin -> {});
        stage.setRemoveConfirmation(plugin -> false);

        button(row(stage, first), "Remove").fire();

        assertThat(plugins).containsExactly(first, second, third);
    }

    @Test
    void badgesAreShownAfterRefresh() {
        PluginStageView<TestPlugin> stage = orderableStage();
        stage.setBadgeProvider(plugin -> plugin == second ? new PluginStageView.Badge("−3", "badge-removed", null) : null);

        stage.refresh();

        Optional<Label> badge = find(row(stage, second), Label.class).stream()
                .filter(label -> label.getStyleClass().contains("badge")).findFirst();
        assertThat(badge).isPresent();
        assertThat(badge.get().getText()).isEqualTo("−3");
        assertThat(badge.get().getStyleClass()).contains("badge-removed");
        assertThat(badge.get().isVisible()).isTrue();
    }

    @Test
    void collapsedStageShowsOnlyTheHeaderWithItsStatus() {
        PluginStageView<TestPlugin> stage = orderableStage();
        stage.setStatusProvider(() -> "1 of 3 enabled");
        assertThat(stage.getHeaderText()).isEqualTo("hint");

        stage.getCollapseButton().fire();

        assertThat(stage.isCollapsed()).isTrue();
        assertThat(stage.getHeaderText()).isEqualTo("1 of 3 enabled");
        assertThat(row(stage, first).getParent().isVisible()).isFalse();
        assertThat(row(stage, first).getParent().isManaged()).isFalse();
        assertThat(stage.getCollapseButton().getAccessibleText()).isEqualTo("Expand Filters");

        stage.getCollapseButton().fire();

        assertThat(stage.isCollapsed()).isFalse();
        assertThat(stage.getHeaderText()).isEqualTo("hint");
        assertThat(row(stage, first).getParent().isVisible()).isTrue();
    }

    @Test
    void clickingTheHeaderTogglesTheStage() {
        PluginStageView<TestPlugin> stage = orderableStage();
        Node header = stage.getChildren().getFirst();

        Event.fireEvent(header, click(1));
        assertThat(stage.isCollapsed()).isTrue();

        Event.fireEvent(header, click(1));
        assertThat(stage.isCollapsed()).isFalse();
    }

    @Test
    void statusIsUpdatedWhenPluginsChange() {
        PluginStageView<TestPlugin> stage = orderableStage();
        stage.setStatusProvider(() -> plugins.size() + " plugins");
        stage.setCollapsed(true);

        plugins.remove(third);

        assertThat(stage.getHeaderText()).isEqualTo("2 plugins");
    }

    @Test
    void enableAllCheckBoxIsOnlyAvailableForStagesWithEnabledPlugins() {
        ObservableList<TestPlugin> inputs = FXCollections.observableArrayList(new TestPlugin("a"));
        PluginStageView<TestPlugin> inputStage = new PluginStageView<>("Inputs", "hint", inputs,
                new SimpleObjectProperty<>(), false, Plugin::getDescription);

        assertThatThrownBy(() -> inputStage.setEnableAllAction(enabled -> {}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(inputStage.getEnableAllCheckBox()).isNull();
    }

    @Test
    void enableAllCheckBoxFollowsTheListOfPlugins() {
        ObservableList<TestPlugin> plugins = FXCollections.observableArrayList();
        PluginStageView<TestPlugin> stage = new PluginStageView<>("Filters", "hint", plugins,
                new SimpleObjectProperty<>(), true, Plugin::getDescription);
        stage.setEnableAllAction(enabled -> plugins.forEach(plugin -> PluginStates.enabled(plugin).set(enabled)));
        assertThat(stage.getEnableAllCheckBox().isDisabled()).as("nothing to enable").isTrue();

        TestPlugin plugin = new TestPlugin("a");
        PluginStates.enabled(plugin).set(true);
        plugins.add(plugin);
        assertThat(stage.getEnableAllCheckBox().isDisabled()).isFalse();
        assertThat(stage.getEnableAllCheckBox().isSelected()).isTrue();

        plugins.add(new TestPlugin("b"));
        assertThat(stage.getEnableAllCheckBox().isIndeterminate()).isTrue();
    }

    @Test
    void theNumberOfTheStageIsShownBeforeItsName() {
        PluginStageView<TestPlugin> stage = orderableStage();
        javafx.scene.control.Label number = (javafx.scene.control.Label) stage.lookup(".stage-number");

        assertThat(number.isVisible()).as("no number by default").isFalse();
        assertThat(stage.getNumber()).isZero();

        stage.setNumber(2);

        assertThat(number.isVisible()).isTrue();
        assertThat(number.getText()).isEqualTo("2");
        assertThat(stage.getNumber()).isEqualTo(2);
        // the number is the leftmost element of the header, also before the check-box enabling all plugins
        stage.setEnableAllAction(enabled -> {
        });
        List<javafx.scene.Node> header = ((javafx.scene.Parent) stage.lookup(".stage-header")).getChildrenUnmodifiable();
        assertThat(header.getFirst()).isSameAs(number);
        assertThat(header.stream().filter(javafx.scene.control.CheckBox.class::isInstance)).isNotEmpty();
    }

    @Test
    void anInactiveStageExplainsWhyInItsHeader() {
        PluginStageView<TestPlugin> stage = orderableStage();
        javafx.scene.Node header = stage.lookup(".stage-header");

        stage.setInactive(true);

        assertThat(stage.isInactive()).isTrue();
        assertThat(header.getProperties().values()).anyMatch(javafx.scene.control.Tooltip.class::isInstance);

        stage.setInactive(false);

        assertThat(stage.isInactive()).isFalse();
        assertThat(header.getProperties().values()).noneMatch(javafx.scene.control.Tooltip.class::isInstance);
    }

    @Test
    void aPluginThatIsNotInstalledIsMarkedAndCanBeRemoved() {
        org.bitpunk.m3uedit.api.Filter missing = new org.bitpunk.m3uedit.plugins.missing.MissingFilter(
                "de.example.RadioFilter", tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        ObservableList<org.bitpunk.m3uedit.api.Filter> filters = FXCollections.observableArrayList(missing);
        // a stage of filters: without add-button, so built-in filters cannot be removed
        PluginStageView<org.bitpunk.m3uedit.api.Filter> stage = new PluginStageView<>("Filters", "hint", filters,
                selection, true, plugin -> null);
        stage.setRemoveConfirmation(plugin -> true);
        HBox row = stage.rowOf(missing).orElseThrow();

        assertThat(find(row, Label.class)).anySatisfy(label -> assertThat(label.getText()).isEqualTo("not installed"));
        assertThat(find(row, CheckBox.class)).singleElement().satisfies(box -> assertThat(box.isDisabled()).isTrue());
        assertThat(find(row, Button.class)).noneMatch(b -> "Configure".equals(b.getAccessibleText()) && b.isVisible());

        button(row, "Remove").fire();

        assertThat(filters).isEmpty();
    }
}

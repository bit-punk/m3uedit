package org.bitpunk.m3uedit.control;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.bitpunk.m3uedit.plugins.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PluginStageViewTest {

    private static class TestPlugin implements Plugin {
        private final String name;
        private final BooleanProperty enabled = new SimpleBooleanProperty(false);
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
        public BooleanProperty getEnabled() {
            return enabled;
        }

        @Override
        public boolean isConfigurable() {
            return true;
        }

        @Override
        public void configure() {
            configured++;
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
        assertThat(third.getEnabled().get()).isTrue();

        third.getEnabled().set(false);
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
        stage.setAddAction(List.of("File Input", "URL Input"), added::add);
        stage.setRemoveConfirmation(plugin -> true);
        selection.set(first);

        button(row(stage, first), "Remove").fire();

        assertThat(plugins).containsExactly(second, third);
        assertThat(selection.get()).isNull();
        assertThat(stage.rowOf(first)).isEmpty();

        stage.getAddButton().getItems().get(1).fire();
        assertThat(added).containsExactly("URL Input");
    }

    @Test
    void removingCanBeCancelled() {
        PluginStageView<TestPlugin> stage = new PluginStageView<>("Inputs", "hint", plugins, selection, false,
                plugin -> "source");
        stage.setAddAction(List.of(), name -> {});
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
}

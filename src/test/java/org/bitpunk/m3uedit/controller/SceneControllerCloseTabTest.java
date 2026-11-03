package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SceneControllerCloseTabTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private interface FxAction {
        void run() throws Exception;
    }

    private void runOnFxThread(FxAction action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private record Fixture(SceneController controller, TabPane tabPane, MenuItem saveMenuItem,
                           MenuItem closeMenuItem, AtomicInteger confirmationCalls) {
    }

    private Fixture loadScene(boolean userConfirmsClose) throws Exception {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
        Scene scene = loader.load();
        SceneController controller = loader.getController();
        AtomicInteger confirmationCalls = new AtomicInteger();
        controller.closeConfirmation = name -> {
            confirmationCalls.incrementAndGet();
            return userConfirmsClose;
        };
        TabPane tabPane = (TabPane) scene.lookup("#tabPane");
        MenuBar menuBar = (MenuBar) scene.lookup(".menu-bar");
        MenuItem saveMenuItem = findMenuItem(menuBar, "saveProjectMenuItem");
        MenuItem closeMenuItem = findMenuItem(menuBar, "closeProjectMenuItem");
        return new Fixture(controller, tabPane, saveMenuItem, closeMenuItem, confirmationCalls);
    }

    private static MenuItem findMenuItem(MenuBar menuBar, String id) {
        return menuBar.getMenus().stream()
                .flatMap(menu -> menu.getItems().stream())
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow();
    }

    private static DataModel createDataModel(String name, boolean changed) {
        DataModel dataModel = DataModelFactory.createDataModel(name);
        dataModel.setChanged(changed);
        return dataModel;
    }

    /**
     * Simulates a click on the close-button of a tab, the same way the TabPane skin does it.
     *
     * @return true if the close-request was NOT vetoed (the TabPane would remove the tab)
     */
    private static boolean fireCloseRequest(Tab tab) {
        Event event = new Event(tab, tab, Tab.TAB_CLOSE_REQUEST_EVENT);
        Event.fireEvent(tab, event);
        return !event.isConsumed();
    }

    @Test
    void unchangedProjectIsClosedWithoutConfirmationViaMenu() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("unchanged", false));
            assertThat(f.saveMenuItem().isDisable()).isFalse();

            f.controller().handleCloseProjectAction();

            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(f.confirmationCalls().get()).isZero();
            assertThat(f.saveMenuItem().isDisable()).isTrue();
            assertThat(f.closeMenuItem().isDisable()).isTrue();
        });
    }

    @Test
    void unchangedProjectIsClosedWithoutConfirmationViaCloseButton() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("unchanged", false));
            Tab tab = f.tabPane().getTabs().getFirst();

            assertThat(fireCloseRequest(tab)).isTrue();
            assertThat(f.confirmationCalls().get()).isZero();

            // the TabPane removes the tab after a non-vetoed request
            f.tabPane().getTabs().remove(tab);
            assertThat(f.saveMenuItem().isDisable()).isTrue();
        });
    }

    @Test
    void changedProjectStaysOpenWhenUserCancelsViaCloseButton() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("changed", true));
            Tab tab = f.tabPane().getTabs().getFirst();

            assertThat(fireCloseRequest(tab)).isFalse();
            assertThat(f.confirmationCalls().get()).isEqualTo(1);
            assertThat(f.tabPane().getTabs()).containsExactly(tab);
            assertThat(f.saveMenuItem().isDisable()).isFalse();
        });
    }

    @Test
    void changedProjectStaysOpenWhenUserCancelsViaMenu() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(false);
            f.controller().addNewProjectTab(createDataModel("changed", true));

            f.controller().handleCloseProjectAction();

            assertThat(f.confirmationCalls().get()).isEqualTo(1);
            assertThat(f.tabPane().getTabs()).hasSize(1);
            assertThat(f.saveMenuItem().isDisable()).isFalse();
        });
    }

    @Test
    void changedProjectIsClosedWhenUserConfirms() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(true);
            f.controller().addNewProjectTab(createDataModel("changed", true));

            f.controller().handleCloseProjectAction();

            assertThat(f.confirmationCalls().get()).isEqualTo(1);
            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(f.saveMenuItem().isDisable()).isTrue();
        });
    }

    @Test
    void closeMenuWithoutOpenProjectDoesNothing() throws Exception {
        runOnFxThread(() -> {
            Fixture f = loadScene(true);

            f.controller().handleCloseProjectAction();

            assertThat(f.tabPane().getTabs()).isEmpty();
            assertThat(f.confirmationCalls().get()).isZero();
        });
    }
}

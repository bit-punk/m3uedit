package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SceneControllerTest {

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    private void runOnFxThread(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
    }

    @Test
    void testSceneFxmlLoadsAndStartScreenIsVisibleInitially() throws Exception {
        runOnFxThread(() -> {
            try {
                Scene scene = FXMLLoader.load(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
                assertThat(scene).isNotNull();

                VBox startScreen = (VBox) scene.lookup("#startScreen");
                TabPane tabPane = (TabPane) scene.lookup("#tabPane");
                Button newProjectButton = (Button) scene.lookup("#newProjectButton");
                Button openProjectButton = (Button) scene.lookup("#openProjectButton");
                Button aboutButton = (Button) scene.lookup("#aboutButton");

                assertThat(startScreen).isNotNull();
                assertThat(tabPane).isNotNull();
                assertThat(newProjectButton).isNotNull();
                assertThat(openProjectButton).isNotNull();
                assertThat(aboutButton).isNotNull();

                SVGPath newProjectSvg = (SVGPath) ((Pane) newProjectButton.getGraphic()).getChildren().get(0);
                SVGPath openProjectSvg = (SVGPath) ((Pane) openProjectButton.getGraphic()).getChildren().get(0);
                SVGPath aboutSvg = (SVGPath) ((Pane) aboutButton.getGraphic()).getChildren().get(0);

                assertThat(newProjectSvg.getContent()).isNotEmpty();
                assertThat(openProjectSvg.getContent()).isNotEmpty();
                assertThat(aboutSvg.getContent()).isNotEmpty();

                assertThat(startScreen.isVisible()).isTrue();
                assertThat(tabPane.isVisible()).isFalse();

                Tab tab = new Tab("Test Tab");
                tabPane.getTabs().add(tab);

                assertThat(startScreen.isVisible()).isFalse();
                assertThat(tabPane.isVisible()).isTrue();

                tabPane.getTabs().remove(tab);

                assertThat(startScreen.isVisible()).isTrue();
                assertThat(tabPane.isVisible()).isFalse();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}

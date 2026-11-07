package org.bitpunk.m3uedit;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.controller.SceneController;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class M3UEditor extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
        Scene scene = loader.load();
        SceneController sceneController = loader.getController();
        primaryStage.setScene(scene);
        // ask before closing the main window if there are unsaved projects
        primaryStage.setOnCloseRequest(event -> {
            if (!sceneController.canApplicationBeClosed()) {
                event.consume();
            }
        });
        primaryStage.getIcons().addAll(loadAppIcons());
        primaryStage.show();
    }

    private static List<Image> loadAppIcons() {
        return Stream.of(16, 32, 48, 64, 128, 256)
                .map(size -> new Image(Objects.requireNonNull(
                        M3UEditor.class.getResourceAsStream("/icons/app/m3uedit-" + size + ".png"))))
                .toList();
    }

}

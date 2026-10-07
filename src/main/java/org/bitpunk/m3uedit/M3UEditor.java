package org.bitpunk.m3uedit;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class M3UEditor extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Scene scene = FXMLLoader.load(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
        primaryStage.setScene(scene);
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

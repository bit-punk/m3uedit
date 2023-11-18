package org.bitpunk.m3uedit;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class M3UEditor extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Scene scene = FXMLLoader.load(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

}

package org.bitpunk.m3uedit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.controller.SceneController;
import org.bitpunk.m3uedit.util.AppIcons;
import org.bitpunk.m3uedit.util.UncaughtExceptionHandler;

import java.util.Objects;

public class M3UEditor extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // show errors not handled anywhere else instead of only printing them to the console
        new UncaughtExceptionHandler().install();
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
        // the main window and all dialogs and other windows opened later on
        AppIcons.applyToAllWindows();
        primaryStage.getIcons().setAll(AppIcons.get());
        primaryStage.show();
        // after the main window has been shown, so it is not delayed and dialogs appear in front of it
        Platform.runLater(sceneController::reopenProjects);
    }

}

package org.bitpunk.m3uedit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.config.Language;
import org.bitpunk.m3uedit.config.Settings;
import org.bitpunk.m3uedit.controller.SceneController;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.util.AppIcons;
import org.bitpunk.m3uedit.util.Themes;
import org.bitpunk.m3uedit.util.UncaughtExceptionHandler;

import java.util.Locale;
import java.util.Objects;

public class M3UEditor extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // show errors not handled anywhere else instead of only printing them to the console
        new UncaughtExceptionHandler().install();
        Settings settings = new Settings();
        useLanguage(settings.getLanguage());
        // the main window and all dialogs and other windows opened later on
        Themes.install(settings.getTheme());
        FXMLLoader loader = Messages.fxmlLoader(Objects.requireNonNull(getClass().getClassLoader().getResource("fxml/Scene.fxml")));
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

    /**
     * Sets the language of the user interface, before any window is created. Also the default locale, so the texts
     * of JavaFX itself (e.g. of standard buttons) and the formats of numbers and dates match it.
     */
    static void useLanguage(Language language) {
        Locale locale = language.locale(Locale.getDefault());
        Locale.setDefault(locale);
        Messages.use(locale);
    }

}

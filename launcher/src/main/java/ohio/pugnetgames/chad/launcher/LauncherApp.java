package ohio.pugnetgames.chad.launcher;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * OrbRunner Launcher — Entry point.
 * A sleek, dark-themed launcher for installing, updating, and launching
 * OrbRunner.
 */
public class LauncherApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        LauncherUI ui = new LauncherUI(primaryStage);
        Scene scene = new Scene(ui.getRoot(), 900, 550);

        // Load CSS
        String css = getClass().getResource("/styles.css").toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setTitle("OrbRunner Launcher");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(450);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

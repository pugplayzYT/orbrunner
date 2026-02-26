package ohio.pugnetgames.chad.launcher;

/**
 * Non-JavaFX entry point so the fat JAR works when double-clicked.
 * JavaFX requires modules on the module path when the main class extends
 * Application directly. This wrapper class sidesteps that restriction.
 */
public class Main {
    public static void main(String[] args) {
        LauncherApp.main(args);
    }
}

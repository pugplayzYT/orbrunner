package ohio.pugnetgames.chad.core;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reads and writes game settings from ~/.orbCollectorGame/settings.dat.
 * Also watches the file for changes made by the launcher while the game is running.
 */
public class SettingsManager {

    public static final Path SETTINGS_FILE = Path.of(
            System.getProperty("user.home"), ".orbCollectorGame", "settings.dat"
    );

    // Settings with defaults matching InGameUI hardcoded defaults
    public float sensitivity  = 0.1f;
    public float fieldOfView  = 60.0f;
    public float fogDensity   = 0.07f;
    public boolean invertY    = false;
    public float masterVolume = 1.0f;

    private Thread watchThread;
    private volatile boolean watching = false;

    public void load() {
        if (!Files.exists(SETTINGS_FILE)) { save(); return; }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
            props.load(in);
            sensitivity  = parseFloat(props, "sensitivity",  sensitivity);
            fieldOfView  = parseFloat(props, "fieldOfView",  fieldOfView);
            fogDensity   = parseFloat(props, "fogDensity",   fogDensity);
            invertY      = Boolean.parseBoolean(props.getProperty("invertY", String.valueOf(invertY)));
            masterVolume = parseFloat(props, "masterVolume", masterVolume);
        } catch (IOException ignored) {}
    }

    public void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            Properties props = new Properties();
            props.setProperty("sensitivity",  String.valueOf(sensitivity));
            props.setProperty("fieldOfView",  String.valueOf(fieldOfView));
            props.setProperty("fogDensity",   String.valueOf(fogDensity));
            props.setProperty("invertY",      String.valueOf(invertY));
            props.setProperty("masterVolume", String.valueOf(masterVolume));
            try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) {
                props.store(out, "OrbRunner Settings");
            }
        } catch (IOException ignored) {}
    }

    /** Watches the settings file for changes written by the launcher. Callback runs on the watcher thread. */
    public void watchForExternalChanges(Consumer<SettingsManager> onChange) {
        if (watching) return;
        watching = true;
        watchThread = new Thread(() -> {
            try {
                Path dir = SETTINGS_FILE.getParent();
                Files.createDirectories(dir);
                try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                    dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                    while (watching) {
                        WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);
                        if (key == null) continue;
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changed = (Path) event.context();
                            if ("settings.dat".equals(changed.getFileName().toString())) {
                                Thread.sleep(60);
                                load();
                                onChange.accept(this);
                            }
                        }
                        key.reset();
                    }
                }
            } catch (Exception ignored) {}
        }, "settings-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void stopWatching() {
        watching = false;
    }

    private float parseFloat(Properties props, String key, float def) {
        try {
            return Float.parseFloat(props.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

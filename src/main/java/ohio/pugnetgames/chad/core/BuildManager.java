package ohio.pugnetgames.chad.core;

import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * BuildManager handles loading build-specific configurations from
 * the 'build.properties' file in the resources.
 *
 * This allows enabling/disabling features at compile time.
 */
public class BuildManager {

    private static final Properties buildProps = new Properties();

    // Static initializer block. This runs ONCE when the class is first
    // loaded by Java, ensuring we only read the file one time.
    static {
        try (InputStream input = BuildManager.class.getClassLoader().getResourceAsStream("build.properties")) {
            if (input == null) {
                System.err.println("FATAL: 'build.properties' not found in resources. Disabling all features.");
            } else {
                buildProps.load(input);
                System.out.println("Build properties loaded successfully.");
            }
        } catch (Exception e) {
            System.err.println("Error loading build properties: " + e.getMessage());
        }
    }

    /**
     * Gets a boolean value from the loaded build.properties file.
     *
     * @param key The property key (e.g., "feature.freecam.enabled")
     * @return The boolean value, or 'false' if not found or invalid.
     */
    public static boolean getBoolean(String key) {
        String value = buildProps.getProperty(key, "false");
        return "true".equalsIgnoreCase(value);
    }

    /**
     * --- NEW: Checks all features and prints their status ---
     * Loops through all properties, and if the key starts with "feature.",
     * it prints whether it's enabled or disabled.
     */
    public static void checkFeatures() {
        System.out.println("--- Checking Build Features ---");

        // Get all property keys
        Set<String> keys = buildProps.stringPropertyNames();

        if (keys.isEmpty()) {
            System.out.println("No properties found in build.properties.");
            return;
        }

        for (String key : keys) {
            // We only care about keys that are features
            if (key.startsWith("feature.")) {
                boolean isEnabled = getBoolean(key);
                if (isEnabled) {
                    System.out.println("  [ENABLED]  " + key);
                } else {
                    System.out.println("  [DISABLED] " + key);
                }
            }
        }
        System.out.println("--- Feature check complete ---");
    }
}
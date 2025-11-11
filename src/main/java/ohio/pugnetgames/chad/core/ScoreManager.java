package ohio.pugnetgames.chad.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * ScoreManager handles saving and loading the best score securely.
 * It uses a simple SHA-256 hash to validate file integrity.
 *
 * FIX: Now saves to a reliable folder in the user's home directory
 * to avoid permissions issues when running from a JAR.
 */
public class ScoreManager {

    // --- NEW: Define a safe, reliable save location ---

    // 1. Get the user's home directory (e.g., C:\Users\Pug)
    private static final String USER_HOME = System.getProperty("user.home");

    // 2. Define our game's save folder (we use "." to make it hidden on Mac/Linux)
    private static final Path SAVE_DIRECTORY = Paths.get(USER_HOME, ".orbCollectorGame");

    // 3. Define the final, absolute path to the save file
    private static final Path BEST_SCORE_FILE_PATH = SAVE_DIRECTORY.resolve("high_score.dat");

    // Secret key for score hashing
    private static final String HASH_SECRET_KEY = "TurboL33tPugplayzKey!";

    /**
     * --- NEW: Helper method to ensure our save folder exists ---
     * This will be called before any read or write operation.
     */
    private static void ensureDataDirExists() {
        try {
            // This does nothing if the folder already exists
            Files.createDirectories(SAVE_DIRECTORY);
        } catch (IOException e) {
            System.err.println("CRITICAL: Could not create save data directory: " + SAVE_DIRECTORY);
            e.printStackTrace();
        }
    }

    /**
     * Generates a SHA-256 hash for the given score and secret key combination.
     */
    private static String hashScore(long score, String key) {
        String data = score + key;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("CRITICAL: SHA-256 not supported. Score saving unsecured.");
            return "";
        }
    }

    /**
     * Loads the best score from the file and validates its integrity.
     * @return The best score, or 0 if the file is invalid or not found.
     */
    public static long loadBestScore() {
        // --- FIX: Ensure the directory exists before we try to read from it ---
        ensureDataDirExists();

        // --- FIX: Use the new absolute Path ---
        try (BufferedReader reader = new BufferedReader(new FileReader(BEST_SCORE_FILE_PATH.toFile()))) {
            String scoreLine = reader.readLine();
            String hashLine = reader.readLine();

            if (scoreLine == null || hashLine == null) {
                System.out.println("Score file corrupt or incomplete. Resetting score.");
                return 0;
            }

            long loadedScore = Long.parseLong(scoreLine.trim());
            String loadedHash = hashLine.trim();

            // Recalculate the expected hash
            String calculatedHash = hashScore(loadedScore, HASH_SECRET_KEY);

            if (calculatedHash.equals(loadedHash)) {
                // System.out.println("Best score loaded securely: " + loadedScore);
                return loadedScore;
            } else {
                System.err.println("SCORE FILE TAMPERED! Hash mismatch. Loaded score invalidated.");
                return 0;
            }

        } catch (FileNotFoundException e) {
            System.out.println("No previous high score found.");
            return 0;
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error reading or parsing score file: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Saves the current best score along with a cryptographic hash for integrity.
     * @param score The score to save.
     */
    public static void saveBestScore(long score) {
        // --- FIX: Ensure the directory exists before we try to write to it ---
        ensureDataDirExists();

        // --- FIX: Use the new absolute Path ---
        try (PrintWriter writer = new PrintWriter(new FileWriter(BEST_SCORE_FILE_PATH.toFile()))) {
            String hash = hashScore(score, HASH_SECRET_KEY);
            writer.println(score); // Line 1: The score value
            writer.println(hash);  // Line 2: The integrity hash
            System.out.println("New best score saved securely to: " + BEST_SCORE_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Error saving best score: " + e.getMessage());
        }
    }
}
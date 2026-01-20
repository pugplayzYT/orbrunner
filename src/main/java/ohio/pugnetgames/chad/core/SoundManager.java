package ohio.pugnetgames.chad.core;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javazoom.jl.player.Player; // Direct JLayer Player import

/**
 * Handles playing a single, looping audio clip for ambiance.
 * MODIFIED: Now also supports playing one-shot sound effects.
 * 💥 MODIFIED: Ambiance now pauses when a one-shot sound is played. 💥
 */
public class SoundManager {

    private Thread ambianceThread;
    private volatile boolean keepPlaying = false;
    private final String AUDIO_FILE_NAME = "ambiance.mp3"; // Reference the file name here

    // 💥 MODIFIED: Resetting to a small delay, as the pause/resume logic is the new fix.
    private static final long AMBIANCE_LOOP_DELAY_MS = 100;

    /**
     * Loads and starts the ambiance track on a continuous loop in a background thread.
     */
    public void loadAndLoopAmbiance() {
        if (ambianceThread != null && ambianceThread.isAlive()) {
            return; // Already running
        }

        keepPlaying = true;
        ambianceThread = new Thread(() -> {
            System.out.println("Ambiance thread started.");

            while (keepPlaying) {
                try {
                    // Use a fresh stream for every loop iteration
                    try (InputStream audioStream = getClass().getClassLoader().getResourceAsStream(AUDIO_FILE_NAME)) {
                        if (audioStream == null) {
                            System.err.println("FATAL: Ambiance sound file '" + AUDIO_FILE_NAME + "' not found in resources. Stopping playback.");
                            keepPlaying = false;
                            break;
                        }

                        // JLayer Player reads from BufferedInputStream
                        try (BufferedInputStream bis = new BufferedInputStream(audioStream)) {
                            Player player = new Player(bis);
                            System.out.println("Playing track: " + AUDIO_FILE_NAME);
                            player.play(); // This is a blocking call until the track ends

                            // Check if playback stopped naturally or was stopped by user
                            if (player.isComplete()) {
                                // If complete, it means we need to loop, so wait briefly
                                try {
                                    Thread.sleep(AMBIANCE_LOOP_DELAY_MS); // Small delay between loops
                                } catch (InterruptedException ignored) {
                                    // Thread interrupted means we should stop
                                    break;
                                }
                            } else {
                                // Player stopped before completion, likely interrupted by stopAmbiance()
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error during MP3 playback loop.");
                    e.printStackTrace();
                    keepPlaying = false; // Stop the loop on error
                }
            }
            System.out.println("Ambiance thread stopped.");
        }, "Ambiance-Player-Thread");

        ambianceThread.setDaemon(true); // Allow application to exit if this is the only thread left
        ambianceThread.start();
    }

    /**
     * Stops and closes the ambiance track by interrupting the player thread.
     */
    public void stopAmbiance() {
        if (ambianceThread != null) {
            keepPlaying = false;
            // Interrupt the playing thread
            if (ambianceThread.isAlive()) {
                ambianceThread.interrupt();
            }
            try {
                ambianceThread.join(500); // Wait up to 500ms for the thread to die gracefully
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ambianceThread = null; // Clear the reference
        }
    }

    /**
     * --- NEW ---
     * Plays a single sound effect one time on a new thread.
     * Pauses ambiance, plays one-shot, then resumes ambiance.
     * @param soundFileName The simple file name (e.g., "crackle.mp3") in resources.
     */
    public void playOneShot(String soundFileName) {
        // 💥 PAUSE AMBIANCE FIRST 💥
        stopAmbiance();

        Thread oneShotThread = new Thread(() -> {
            try (InputStream audioStream = getClass().getClassLoader().getResourceAsStream(soundFileName)) {
                if (audioStream == null) {
                    System.err.println("ERROR: One-shot sound file '" + soundFileName + "' not found in resources.");
                    return;
                }

                try (BufferedInputStream bis = new BufferedInputStream(audioStream)) {
                    Player player = new Player(bis);
                    System.out.println("Playing one-shot: " + soundFileName);
                    player.play(); // Blocking call until track ends
                }
            } catch (Exception e) {
                System.err.println("Error during one-shot MP3 playback.");
                e.printStackTrace();
            } finally {
                // 💥 RESUME AMBIANCE AFTER PLAYBACK 💥
                // This is guaranteed to run after the one-shot is finished.
                loadAndLoopAmbiance();
            }
        }, "OneShot-Player-Thread");

        oneShotThread.setDaemon(true); // Don't prevent app from closing
        oneShotThread.start();
    }
}
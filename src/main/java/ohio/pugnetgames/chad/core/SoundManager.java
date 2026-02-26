package ohio.pugnetgames.chad.core;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javazoom.jl.player.Player;
import javax.sound.sampled.*;

/**
 * Handles playing a single, looping audio clip for ambiance.
 * Also supports playing one-shot sound effects and master volume control.
 */
public class SoundManager {

    private Thread ambianceThread;
    private volatile boolean keepPlaying = false;
    private volatile Player currentPlayer = null;
    private final String AUDIO_FILE_NAME = "ambiance.mp3";
    private static final long AMBIANCE_LOOP_DELAY_MS = 100;

    // Volume control (0.0 = mute, 1.0 = full)
    private volatile float masterVolume = 1.0f;

    /**
     * Sets the master volume (0.0 to 1.0).
     * Applies immediately to the system mixer output.
     */
    public void setVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        applyVolumeToMixer();
    }

    public float getVolume() {
        return masterVolume;
    }

    /**
     * Applies volume to all available output lines via javax.sound.sampled.
     * JLayer creates its own SourceDataLine internally, so we find it
     * through the mixer and adjust gain on any active lines.
     */
    private void applyVolumeToMixer() {
        try {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            for (Mixer.Info info : mixerInfos) {
                try {
                    Mixer mixer = AudioSystem.getMixer(info);
                    Line[] lines = mixer.getSourceLines();
                    for (Line line : lines) {
                        if (line instanceof SourceDataLine) {
                            setLineVolume((SourceDataLine) line);
                        } else if (line instanceof Clip) {
                            setLineVolume((Clip) line);
                        }
                    }
                } catch (Exception ignored) {
                    // Some mixers don't support enumeration
                }
            }
        } catch (Exception e) {
            // Volume control not available on this platform — not fatal
        }
    }

    private void setLineVolume(DataLine line) {
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                // Convert linear volume (0-1) to decibels
                // Gain range is typically -80dB to +6dB
                float minGain = gainControl.getMinimum();
                float maxGain = gainControl.getMaximum();
                float gain;
                if (masterVolume <= 0.001f) {
                    gain = minGain; // Effectively mute
                } else {
                    // Logarithmic scale: feels natural to the ear
                    gain = (float) (20.0 * Math.log10(masterVolume));
                    gain = Math.max(minGain, Math.min(maxGain, gain));
                }
                gainControl.setValue(gain);
            }
        } catch (Exception ignored) {
            // Not all lines support MASTER_GAIN
        }
    }

    /**
     * Loads and starts the ambiance track on a continuous loop.
     */
    public void loadAndLoopAmbiance() {
        if (ambianceThread != null && ambianceThread.isAlive()) {
            return;
        }

        keepPlaying = true;
        ambianceThread = new Thread(() -> {
            System.out.println("Ambiance thread started.");

            while (keepPlaying) {
                try {
                    try (InputStream audioStream = getClass().getClassLoader().getResourceAsStream(AUDIO_FILE_NAME)) {
                        if (audioStream == null) {
                            System.err.println(
                                    "FATAL: Ambiance sound file '" + AUDIO_FILE_NAME + "' not found in resources.");
                            keepPlaying = false;
                            break;
                        }

                        try (BufferedInputStream bis = new BufferedInputStream(audioStream)) {
                            Player player = new Player(bis);
                            currentPlayer = player;
                            System.out.println("Playing track: " + AUDIO_FILE_NAME);

                            // Apply volume shortly after playback starts
                            // (JLayer creates the audio line lazily)
                            Thread volumeApplier = new Thread(() -> {
                                try {
                                    Thread.sleep(200); // Wait for JLayer to open the line
                                    applyVolumeToMixer();
                                } catch (InterruptedException ignored) {
                                }
                            });
                            volumeApplier.setDaemon(true);
                            volumeApplier.start();

                            player.play();
                            currentPlayer = null;

                            if (player.isComplete()) {
                                try {
                                    Thread.sleep(AMBIANCE_LOOP_DELAY_MS);
                                } catch (InterruptedException ignored) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error during MP3 playback loop.");
                    e.printStackTrace();
                    keepPlaying = false;
                }
            }
            System.out.println("Ambiance thread stopped.");
        }, "Ambiance-Player-Thread");

        ambianceThread.setDaemon(true);
        ambianceThread.start();
    }

    /**
     * Stops the ambiance track.
     */
    public void stopAmbiance() {
        if (ambianceThread != null) {
            keepPlaying = false;
            if (currentPlayer != null) {
                currentPlayer.close();
                currentPlayer = null;
            }
            if (ambianceThread.isAlive()) {
                ambianceThread.interrupt();
            }
            try {
                ambianceThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ambianceThread = null;
        }
    }

    /**
     * Plays a single sound effect, pausing ambiance during playback.
     */
    public void playOneShot(String soundFileName) {
        stopAmbiance();

        Thread oneShotThread = new Thread(() -> {
            try (InputStream audioStream = getClass().getClassLoader().getResourceAsStream(soundFileName)) {
                if (audioStream == null) {
                    System.err.println("ERROR: One-shot sound file '" + soundFileName + "' not found.");
                    return;
                }

                try (BufferedInputStream bis = new BufferedInputStream(audioStream)) {
                    Player player = new Player(bis);
                    System.out.println("Playing one-shot: " + soundFileName);

                    // Apply volume after line opens
                    Thread volumeApplier = new Thread(() -> {
                        try {
                            Thread.sleep(200);
                            applyVolumeToMixer();
                        } catch (InterruptedException ignored) {
                        }
                    });
                    volumeApplier.setDaemon(true);
                    volumeApplier.start();

                    player.play();
                }
            } catch (Exception e) {
                System.err.println("Error during one-shot MP3 playback.");
                e.printStackTrace();
            } finally {
                loadAndLoopAmbiance();
            }
        }, "OneShot-Player-Thread");

        oneShotThread.setDaemon(true);
        oneShotThread.start();
    }
}
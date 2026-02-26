package ohio.pugnetgames.chad.core;

import java.nio.file.Path;

/**
 * Represents a single named run — one player-created playthrough with its own
 * difficulty, save state, and completion record.
 */
public class RunData {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED   = "COMPLETED";

    /** Sanitized folder name used as the on-disk identifier. */
    public final String id;

    /** The name the player typed, e.g. "Extra Cool Run 5000". */
    public String displayName;

    public Difficulty difficulty;

    /** Epoch ms when the run was created. */
    public long createdAt;

    /** Epoch ms when the run was completed; 0 if still in progress. */
    public long completedAt;

    /** {@link #STATUS_IN_PROGRESS} or {@link #STATUS_COMPLETED}. */
    public String status;

    /**
     * Total played time in milliseconds, accumulated across sessions.
     * Updated on every save and set definitively on completion.
     */
    public long elapsedMs;

    /** Absolute path to this run's save folder. */
    public final Path folderPath;

    public RunData(String id, String displayName, Difficulty difficulty,
                   long createdAt, long completedAt, String status,
                   long elapsedMs, Path folderPath) {
        this.id          = id;
        this.displayName = displayName;
        this.difficulty  = difficulty;
        this.createdAt   = createdAt;
        this.completedAt = completedAt;
        this.status      = status;
        this.elapsedMs   = elapsedMs;
        this.folderPath  = folderPath;
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /** Returns elapsed time as a human-readable string, e.g. "14m 32s". */
    public String formatElapsed() {
        long totalSec = elapsedMs / 1000;
        long mins     = totalSec / 60;
        long secs     = totalSec % 60;
        if (mins > 0) {
            return mins + "m " + secs + "s";
        }
        return secs + "s";
    }
}

package ohio.pugnetgames.chad.core;

/**
 * A snapshot of an in-progress run's world and player state.
 * Saved to disk when the player quits to the main menu and
 * restored when they continue the run.
 */
public class RunState {

    /** The seed used to generate this run's world. */
    public final long worldSeed;

    /** Player position at the time of saving. */
    public final float playerX;
    public final float playerY;
    public final float playerZ;

    /** Camera orientation at the time of saving. */
    public final float yaw;
    public final float pitch;

    /**
     * Which keys have been collected, indexed by key spawn order.
     * Length == TOTAL_KEYS for this run's difficulty.
     */
    public final boolean[] keysCollected;

    public RunState(long worldSeed, float playerX, float playerY, float playerZ,
                    float yaw, float pitch, boolean[] keysCollected) {
        this.worldSeed     = worldSeed;
        this.playerX       = playerX;
        this.playerY       = playerY;
        this.playerZ       = playerZ;
        this.yaw           = yaw;
        this.pitch         = pitch;
        this.keysCollected = keysCollected;
    }
}

package ohio.pugnetgames.chad.game;

/**
 * Represents a collectable Key.
 * Keys sit on the floor and rotate.
 */
public class Key {
    public float x, y, z; // 3D position
    public boolean collected = false;
    public float rotation = 0.0f; // Current rotation for rendering

    // MODIFIED: Removed keyHeight, it's not needed

    // MODIFIED: Constructor now takes X, Y, Z
    public Key(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
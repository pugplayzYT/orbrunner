package ohio.pugnetgames.chad.game;

import java.util.ArrayList;
import java.util.List;

/**
 * A data class that represents a single generated room.
 * It stores its boundaries and which walls have tunnels.
 */
public class Room {

    /** NEW: Different types of rooms for different generation rules/visuals. */
    public enum RoomType {
        STANDARD, COURTYARD
    }

    // The boundaries of the room
    public float minX, minZ, maxX, maxZ;

    // Flags to track which walls have connections
    public boolean northWallUsed = false;
    public boolean southWallUsed = false;
    public boolean eastWallUsed = false;
    public boolean westWallUsed = false;

    // NEW: Room type
    private final RoomType type;

    public Room(float minX, float minZ, float maxX, float maxZ) {
        this(minX, minZ, maxX, maxZ, RoomType.STANDARD);
    }

    public Room(float minX, float minZ, float maxX, float maxZ, RoomType type) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.type = type;
    }

    public RoomType getType() {
        return type;
    }

    /**
     * Checks if this room overlaps with another room, using a specified padding
     * to ensure minimum separation distance.
     */
    public boolean overlaps(Room otherRoom, float padding) {
        // Check for overlap on the X-axis
        boolean overlapX = this.maxX + padding > otherRoom.minX &&
                this.minX - padding < otherRoom.maxX;

        // Check for overlap on the Z-axis
        boolean overlapZ = this.maxZ + padding > otherRoom.minZ &&
                this.minZ - padding < otherRoom.maxZ;

        return overlapX && overlapZ;
    }

    /**
     * @return The center X coordinate of the room.
     */
    public float getCenterX() {
        return (minX + maxX) / 2.0f;
    }

    /**
     * @return The center Z coordinate of the room.
     */
    public float getCenterZ() {
        return (minZ + maxZ) / 2.0f;
    }

    /**
     * @return The width (X-axis) of the room.
     */
    public float getWidth() {
        return maxX - minX;
    }

    /**
     * @return The depth (Z-axis) of the room.
     */
    public float getDepth() {
        return maxZ - minZ;
    }

    /**
     * Marks a wall as "used" so another tunnel can't generate there.
     * @param dir The direction of the wall to mark.
     */
    public void markWallUsed(Direction dir) {
        switch (dir) {
            case NORTH: northWallUsed = true; break;
            case SOUTH: southWallUsed = true; break;
            case EAST:  eastWallUsed = true;  break;
            case WEST:  westWallUsed = true;  break;
        }
    }

    /**
     * NEW: Unmarks a wall as "used" (used for collision rollback).
     * @param dir The direction of the wall to unmark.
     */
    public void unmarkWallUsed(Direction dir) {
        switch (dir) {
            case NORTH: northWallUsed = false; break;
            case SOUTH: southWallUsed = false; break;
            case EAST:  eastWallUsed = false;  break;
            case WEST:  westWallUsed = false;  break;
        }
    }

    /**
     * @return A List of all directions that do *not* have a tunnel yet.
     */
    public List<Direction> getAvailableWalls() {
        // MODIFIED: Removed the check that prevented Courtyard rooms from branching further.

        List<Direction> walls = new ArrayList<>();
        if (!northWallUsed) walls.add(Direction.NORTH);
        if (!southWallUsed) walls.add(Direction.SOUTH);
        if (!eastWallUsed)  walls.add(Direction.EAST);
        if (!westWallUsed)  walls.add(Direction.WEST);
        return walls;
    }
}
package ohio.pugnetgames.chad.game;

/**
 * Represents the four cardinal directions for map generation.
 */
public enum Direction {
    NORTH, SOUTH, EAST, WEST;

    /**
     * @return The opposite direction (e.g., NORTH returns SOUTH).
     */
    public Direction getOpposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case EAST:  return WEST;
            case WEST:  return EAST;
            default:    return null; // Should never happen
        }
    }
}
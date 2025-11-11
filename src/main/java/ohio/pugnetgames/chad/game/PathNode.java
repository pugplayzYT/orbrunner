package ohio.pugnetgames.chad.game;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom Node class for our DIY A* / BFS pathfinding.
 * This represents one cell in our navigation grid.
 */
public class PathNode {

    // Grid coordinates
    public int x;
    public int z;

    // World coordinates
    public float worldX;
    public float worldZ;

    // Pathfinding data
    public boolean isWalkable;
    public List<PathNode> neighbors;
    public PathNode parent; // Used to reconstruct the path

    public PathNode(int x, int z, float worldX, float worldZ, boolean isWalkable) {
        this.x = x;
        this.z = z;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.isWalkable = isWalkable;
        this.neighbors = new ArrayList<>();
        this.parent = null;
    }

    /**
     * Connects this node to a neighbor.
     */
    public void addNeighbor(PathNode neighbor) {
        if (neighbor != null && neighbor.isWalkable) {
            this.neighbors.add(neighbor);
        }
    }

    /**
     * Resets the node for a new pathfinding search.
     */
    public void reset() {
        this.parent = null;
    }
}
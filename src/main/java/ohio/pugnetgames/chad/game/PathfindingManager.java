package ohio.pugnetgames.chad.game;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/**
 * Manages the A* pathfinding grid and logic.
 * This class builds a 2D grid representation of the 3D world,
 * allowing the A* algorithm to find paths around obstacles.
 *
 * This is our DIY version, no libraries needed!
 */
public class PathfindingManager {

    // --- Grid ---
    private PathNode[][] grid;
    private int gridWidth = 0;
    private int gridDepth = 0;

    // --- World-to-Grid Mapping ---
    private float worldMinX = 0;
    private float worldMinZ = 0;
    // --- 💥 NEW: Store Max Bounds (Needed for Minimap) 💥 ---
    private float worldMaxX = 0;
    private float worldMaxZ = 0;
    // --- 💥 END NEW 💥 ---

    // ---
    // --- 1. YOUR PREFERRED RESOLUTION 徴 ---
    // ---
    private static final float GRID_RESOLUTION = 0.2f; // Your high-res value
    private static final float INSET_AMOUNT = 0.21f; // Must be > GRID_RESOLUTION
    // ---
    // --- 徴 END OF CHANGE 徴 ---
    // ---

    // --- Path Cache ---
    private List<PathNode> currentPath;

    public PathfindingManager() {
        // Constructor is now empty
    }

    /**
     * Analyzes the entire world and builds a 2D navigation grid.
     * All collidable objects are marked as "unwalkable".
     *
     * @param world The World object containing all rooms and static objects.
     */
    public void buildGrid(World world) {
        System.out.println("[PathfindingManager] Starting grid build...");

        // 1. Find the total bounds of the world
        worldMinX = Float.MAX_VALUE;
        worldMinZ = Float.MAX_VALUE;
        worldMaxX = Float.MIN_VALUE; // 💥 FIX: Use the class field now 💥
        worldMaxZ = Float.MIN_VALUE; // 💥 FIX: Use the class field now 💥

        for (Room room : world.getAllRooms()) {
            if (room.minX < worldMinX) worldMinX = room.minX;
            if (room.minZ < worldMinZ) worldMinZ = room.minZ;
            if (room.maxX > worldMaxX) worldMaxX = room.maxX;
            if (room.maxZ > worldMaxZ) worldMaxZ = room.maxZ;
        }
        for (GameObject obj : world.getStaticObjects()) {
            float objMinX = obj.getPosX() - obj.getScaleX() / 2.0f;
            float objMaxX = obj.getPosX() + obj.getScaleX() / 2.0f;
            float objMinZ = obj.getPosZ() - obj.getScaleZ() / 2.0f;
            float objMaxZ = obj.getPosZ() + obj.getScaleZ() / 2.0f;

            if (objMinX < worldMinX) worldMinX = objMinX;
            if (objMinZ < worldMinZ) worldMinZ = objMinZ;
            if (objMaxX > worldMaxX) worldMaxX = objMaxX;
            if (objMaxZ > worldMaxZ) worldMaxZ = objMaxZ;
        }

        // Add a 5-unit buffer for safety
        worldMinX -= 5.0f;
        worldMinZ -= 5.0f;
        worldMaxX += 5.0f;
        worldMaxZ += 5.0f;

        // 2. Calculate grid dimensions
        gridWidth = (int) ((worldMaxX - worldMinX) / GRID_RESOLUTION);
        gridDepth = (int) ((worldMaxZ - worldMinZ) / GRID_RESOLUTION);

        if (gridWidth <= 0 || gridDepth <= 0) {
            System.err.println("[PathfindingManager] FATAL: Invalid grid dimensions!");
            return;
        }

        System.out.println("[PathfindingManager] Grid Dimensions: " + gridWidth + "x" + gridDepth);
        grid = new PathNode[gridWidth][gridDepth];

        // 3. 徴FIX: Initialize all cells as UNWALKABLE (the void)
        for (int x = 0; x < gridWidth; x++) {
            for (int z = 0; z < gridDepth; z++) {
                float worldX = gridToWorldX(x);
                float worldZ = gridToWorldZ(z);
                // Default to false (unwalkable)
                grid[x][z] = new PathNode(x, z, worldX, worldZ, false);
            }
        }

        // 4. 徴NEW: Mark WALKABLE floors
        // This is a "painting" pass. We find all floor objects and
        // mark the nodes they cover as walkable.
        System.out.println("[PathfindingManager] Marking walkable floor nodes...");
        for (GameObject obj : world.getStaticObjects()) {

            boolean isPlaneFloor = obj.getShape() == GameObject.ShapeType.PLANE;
            boolean isCubeFloor = obj.getShape() == GameObject.ShapeType.CUBE && obj.getScaleY() <= 0.5f;

            if (isPlaneFloor || isCubeFloor) {
                // ---
                // --- 徴 FIX 1: SELECTIVE FLOOR PAINTING 徴 ---
                // ---
                float objMinX, objMaxX, objMinZ, objMaxZ;

                if (isPlaneFloor) {
                    // PLANES (courtyards, rooms) must SHRINK to prevent "void paths"
                    objMinX = obj.getPosX() - (obj.getScaleX() / 2.0f) + INSET_AMOUNT; // <-- PLUS
                    objMaxX = obj.getPosX() + (obj.getScaleX() / 2.0f) - INSET_AMOUNT; // <-- MINUS
                    objMinZ = obj.getPosZ() - (obj.getScaleZ() / 2.0f) + INSET_AMOUNT; // <-- PLUS
                    objMaxZ = obj.getPosZ() + (obj.getScaleZ() / 2.0f) - INSET_AMOUNT; // <-- MINUS
                } else {
                    // CUBE floors (tunnels) must EXPAND on ends, SHRINK on sides
                    float objScaleX = obj.getScaleX();
                    float objScaleZ = obj.getScaleZ();

                    if (objScaleX > objScaleZ) {
                        // This is an EAST-WEST tunnel (wider than it is deep)
                        // EXPAND on X-axis (the ends)
                        objMinX = obj.getPosX() - (objScaleX / 2.0f) - INSET_AMOUNT; // <-- MINUS (Expand)
                        objMaxX = obj.getPosX() + (objScaleX / 2.0f) + INSET_AMOUNT; // <-- PLUS (Expand)
                        // SHRINK on Z-axis (the sides)
                        objMinZ = obj.getPosZ() - (objScaleZ / 2.0f) + INSET_AMOUNT; // <-- PLUS (Shrink)
                        objMaxZ = obj.getPosZ() + (obj.getScaleZ() / 2.0f) - INSET_AMOUNT; // <-- MINUS (Shrink)
                    } else {
                        // This is a NORTH-SOUTH tunnel (deeper than it is wide)
                        // SHRINK on X-axis (the sides)
                        objMinX = obj.getPosX() - (objScaleX / 2.0f) + INSET_AMOUNT; // <-- PLUS (Shrink)
                        objMaxX = obj.getPosX() + (obj.getScaleX() / 2.0f) - INSET_AMOUNT; // <-- MINUS (Shrink)
                        // EXPAND on Z-axis (the ends)
                        objMinZ = obj.getPosZ() - (objScaleZ / 2.0f) - INSET_AMOUNT; // <-- MINUS (Expand)
                        objMaxZ = obj.getPosZ() + (obj.getScaleZ() / 2.0f) + INSET_AMOUNT; // <-- PLUS (Expand)
                    }
                }
                // ---
                // --- 徴 END OF FIX 1 徴 ---
                // ---

                int gridMinX = worldToGridX(objMinX);
                int gridMaxX = worldToGridX(objMaxX);
                int gridMinZ = worldToGridZ(objMinZ);
                int gridMaxZ = worldToGridZ(objMaxZ);

                for (int x = gridMinX; x <= gridMaxX; x++) {
                    for (int z = gridMinZ; z <= gridMaxZ; z++) {
                        if (isGridCoordValid(x, z)) {
                            grid[x][z].isWalkable = true;
                        }
                    }
                }
            }
        }

        // 5. 徴NEW: Mark UNWALKABLE obstacles
        // This is an "erasing" pass. We find all collidable objects
        // and punch holes in the walkable areas.
        System.out.println("[PathfindingManager] Marking unwalkable obstacle nodes...");
        for (GameObject obj : world.getStaticObjects()) {

            // This logic is correct
            boolean isFloor = (obj.getShape() == GameObject.ShapeType.PLANE) ||
                    (obj.getShape() == GameObject.ShapeType.CUBE && obj.getScaleY() <= 0.5f);

            // If it's collidable AND it's NOT a floor, it's an obstacle.

            // --- 💥 THE FIX: Check the object's Y-level! ---
            // Calculate the object's base Y-level (its lowest point)
            // (The lintel's base Y-level is 3.0f, wall bases are 0.0f)
            float objectBaseY = obj.getPosY() - (obj.getScaleY() / 2.0f);

            // We only mark obstacles as unwalkable if their base is on the
            // ground (less than 3.0f, which is the tunnel height).
            // This stops LINTELS (the object *over* the door) from
            // blocking the path, since their baseY is exactly 3.0f.
            if (obj.isCollidable() && !isFloor && objectBaseY < 3.0f) {
                // --- 💥 END OF FIX ---

                // ---
                // --- 💥 FINAL FIX: Erase the EXACT footprint ---
                // ---
                // We do NOT modify the obstacle's bounds here.
                // The "painting" pass (Step 4) already created the
                // necessary buffers by shrinking/expanding floors.
                // Erasing the exact wall footprint will now leave
                // a perfect, small unwalkable gap on the sides
                // and create a perfect connection at the doorways.
                // --- 💥 END OF FIX ---

                float objMinX = obj.getPosX() - (obj.getScaleX() / 2.0f);
                float objMaxX = obj.getPosX() + (obj.getScaleX() / 2.0f);
                float objMinZ = obj.getPosZ() - (obj.getScaleZ() / 2.0f);
                float objMaxZ = obj.getPosZ() + (obj.getScaleZ() / 2.0f);

                int gridMinX = worldToGridX(objMinX);
                int gridMaxX = worldToGridX(objMaxX);
                int gridMinZ = worldToGridZ(objMinZ);
                int gridMaxZ = worldToGridZ(objMaxZ);

                for (int x = gridMinX; x <= gridMaxX; x++) {
                    for (int z = gridMinZ; z <= gridMaxZ; z++) {
                        if (isGridCoordValid(x, z)) {
                            grid[x][z].isWalkable = false; // Punch a hole
                        }
                    }
                }
            }
        }

        // 6. Build the neighbor connections for every node
        buildNeighbors();
        System.out.println("[PathfindingManager] Grid build complete.");
    }

    /**
     * Loops through the generated grid and connects all walkable nodes
     * to their walkable neighbors.
     */
    private void buildNeighbors() {
        for (int x = 0; x < gridWidth; x++) {
            for (int z = 0; z < gridDepth; z++) {
                if (grid[x][z] == null || !grid[x][z].isWalkable) continue;

                // Check North
                if (isGridCoordValid(x, z - 1) && grid[x][z - 1] != null && grid[x][z - 1].isWalkable) {
                    grid[x][z].addNeighbor(grid[x][z - 1]);
                }
                // Check South
                if (isGridCoordValid(x, z + 1) && grid[x][z + 1] != null && grid[x][z + 1].isWalkable) {
                    grid[x][z].addNeighbor(grid[x][z + 1]);
                }
                // Check West
                if (isGridCoordValid(x - 1, z) && grid[x - 1][z] != null && grid[x - 1][z].isWalkable) {
                    grid[x][z].addNeighbor(grid[x - 1][z]);
                }
                // Check East
                if (isGridCoordValid(x + 1, z) && grid[x + 1][z] != null && grid[x + 1][z].isWalkable) {
                    grid[x][z].addNeighbor(grid[x + 1][z]);
                }
                // TODO: Add diagonal neighbors if desired
            }
        }
        System.out.println("[PathfindingManager] Neighbor connections built.");
    }

    /**
     * Finds the closest node on the grid to the given world coordinates.
     */
    private PathNode findNearestNode(float worldX, float worldZ) {
        int gridX = worldToGridX(worldX);
        int gridZ = worldToGridZ(worldZ);

        if (!isGridCoordValid(gridX, gridZ)) {
            return null; // Out of bounds
        }

        PathNode node = grid[gridX][gridZ];

        // If the node is unwalkable, spiral out to find the nearest walkable one
        if (node == null || !node.isWalkable) {
            System.out.println("[PathfindingManager] Target node unwalkable, searching for nearby...");
            Queue<PathNode> searchQueue = new LinkedList<>();
            Set<PathNode> visited = new HashSet<>();

            if(node != null) { // Start search from the unwalkable node
                searchQueue.add(node);
                visited.add(node);
            } else { // This should not happen, but as a fallback
                return null;
            }

            while(!searchQueue.isEmpty()) {
                PathNode currentNode = searchQueue.poll();
                if (currentNode.isWalkable) {
                    System.out.println("[PathfindingManager] Found nearby walkable node at (" + currentNode.x + ", " + currentNode.z + ")");
                    return currentNode; // Found it
                }
                // Add unvisited 4-directional neighbors
                int x = currentNode.x;
                int z = currentNode.z;
                if (isGridCoordValid(x, z-1) && grid[x][z-1] != null && !visited.contains(grid[x][z-1])) { searchQueue.add(grid[x][z-1]); visited.add(grid[x][z-1]); }
                if (isGridCoordValid(x, z+1) && grid[x][z+1] != null && !visited.contains(grid[x][z+1])) { searchQueue.add(grid[x][z+1]); visited.add(grid[x][z+1]); }
                if (isGridCoordValid(x-1, z) && grid[x-1][z] != null && !visited.contains(grid[x-1][z])) { searchQueue.add(grid[x-1][z]); visited.add(grid[x-1][z]); }
                if (isGridCoordValid(x+1, z) && grid[x+1][z] != null && !visited.contains(grid[x+1][z])) { searchQueue.add(grid[x+1][z]); visited.add(grid[x+1][z]); }
            }
            System.err.println("[PathfindingManager] Nearby search failed to find any walkable node.");
            return null; // No walkable node found anywhere?
        }

        return node; // Original node was fine
    }

    /**
     * Finds a path from a start to an end location in world coordinates.
     * @return A List of PathNodes representing the path, or null if no path is found.
     */
    public List<PathNode> findPath(float startX, float startZ, float endX, float endZ) {
        if (grid == null) {
            System.err.println("[PathfindingManager] findPath called before grid was built!");
            return null;
        }

        // 1. Find the start and end nodes on the grid
        PathNode startNode = findNearestNode(startX, startZ);
        PathNode endNode = findNearestNode(endX, endZ);

        if (startNode == null || endNode == null) {
            System.err.println("[PathfindingManager] Invalid start or end node (is null).");
            return null;
        }

        // 2. Run the Breadth-First Search (BFS)
        System.out.println("[PathfindingManager] Finding path from (" + startNode.x + ", " + startNode.z + ") to (" + endNode.x + ", " + endNode.z + ")");
        Map<PathNode, PathNode> parentMap = bfs(startNode, endNode);

        // 3. Reconstruct the path from the parent map
        currentPath = reconstructPath(parentMap, startNode, endNode);

        if (currentPath == null || currentPath.isEmpty()) {
            System.err.println("[PathfindingManager] No path found.");
        } else {
            System.out.println("[PathfindingManager] Path found with " + currentPath.size() + " nodes.");
        }

        return currentPath;
    }

    /**
     * Performs a Breadth-First Search to find the shortest path.
     * @return A Map where each key is a node and its value is the parent
     * node we took to get there.
     */
    private Map<PathNode, PathNode> bfs(PathNode start, PathNode end) {
        // Reset all nodes for this search
        for (int x = 0; x < gridWidth; x++) {
            for (int z = 0; z < gridDepth; z++) {
                if(grid[x][z] != null) {
                    grid[x][z].reset();
                }
            }
        }

        Queue<PathNode> queue = new LinkedList<>();
        Set<PathNode> visited = new HashSet<>();
        Map<PathNode, PathNode> parentMap = new HashMap<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            PathNode currentNode = queue.poll();

            if (currentNode == end) {
                return parentMap; // Found the end!
            }

            for (PathNode neighbor : currentNode.neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, currentNode); // Log how we got here
                    queue.add(neighbor);
                }
            }
        }

        return parentMap; // Return map even if path not found
    }

    /**
     * Reconstructs the path backwards from the end node.
     */
    private List<PathNode> reconstructPath(Map<PathNode, PathNode> parentMap, PathNode start, PathNode end) {
        List<PathNode> path = new ArrayList<>();
        PathNode current = end;

        if (!parentMap.containsKey(end) && start != end) {
            return null; // 'end' node was never reached
        }

        while (current != null) {
            path.add(0, current); // Add to the front of the list
            if (current == start) {
                return path; // We're back at the start
            }
            current = parentMap.get(current);
        }

        return null; // Should not happen if start == end or parentMap is correct
    }

    /**
     * Checks if a grid coordinate is within the valid grid array bounds.
     */
    public boolean isGridCoordValid(int x, int z) {
        return x >= 0 && x < gridWidth && z >= 0 && z < gridDepth;
    }

    // --- Coordinate Conversion Helpers ---

    public int worldToGridX(float worldX) {
        int gridX = (int) ((worldX - worldMinX) / GRID_RESOLUTION);
        return Math.max(0, Math.min(gridWidth - 1, gridX)); // Clamp to bounds
    }

    public int worldToGridZ(float worldZ) {
        int gridZ = (int) ((worldZ - worldMinZ) / GRID_RESOLUTION);
        return Math.max(0, Math.min(gridDepth - 1, gridZ)); // Clamp to bounds
    }

    private float gridToWorldX(int gridX) {
        // Return the *center* of the grid cell
        return (gridX * GRID_RESOLUTION) + worldMinX + (GRID_RESOLUTION / 2.0f);
    }

    private float gridToWorldZ(int gridZ) {
        // Return the *center* of the grid cell
        return (gridZ * GRID_RESOLUTION) + worldMinZ + (GRID_RESOLUTION / 2.0f);
    }

    /**
     * Gets the last calculated path for debug rendering.
     */
    public List<PathNode> getPath() {
        return currentPath;
    }

    /**
     * Gets the entire grid for debug rendering.
     */
    public PathNode[][] getGrid() {
        return grid;
    }

    // --- 💥 NEW GETTERS FOR MINIMAP V2 BOUNDING BOX 💥 ---
    public float getWorldMinX() { return worldMinX; }
    public float getWorldMinZ() { return worldMinZ; }
    public float getWorldMaxX() { return worldMaxX; }
    public float getWorldMaxZ() { return worldMaxZ; }
    // --- 💥 END NEW GETTERS 💥 ---

    /**
     * NEW: Updates the grid to make an area (like a door) walkable
     * and rebuilds neighbor connections for that area.
     * This is called when the escape door opens.
     * @param door The GameObject representing the door to open.
     */
    public void openDoorInGrid(GameObject door) {
        if (grid == null || door == null) {
            return;
        }

        System.out.println("[PathfindingManager] Updating grid: Opening door...");

        // 1. Get the exact footprint of the door
        // (Copied from the "erase" pass in buildGrid)
        float objMinX = door.getPosX() - (door.getScaleX() / 2.0f);
        float objMaxX = door.getPosX() + (door.getScaleX() / 2.0f);
        float objMinZ = door.getPosZ() - (door.getScaleZ() / 2.0f);
        float objMaxZ = door.getPosZ() + (door.getScaleZ() / 2.0f);

        int gridMinX = worldToGridX(objMinX);
        int gridMaxX = worldToGridX(objMaxX);
        int gridMinZ = worldToGridZ(objMinZ);
        int gridMaxZ = worldToGridZ(objMaxZ);

        // 2. Mark all nodes in this footprint as WALKABLE
        for (int x = gridMinX; x <= gridMaxX; x++) {
            for (int z = gridMinZ; z <= gridMaxZ; z++) {
                if (isGridCoordValid(x, z) && grid[x][z] != null) {
                    grid[x][z].isWalkable = true; // "Open" the door
                }
            }
        }

        // 3. Re-build neighbor connections for this area AND its immediate surroundings
        // (We must check a slightly larger area to connect the new nodes to the old floor)
        int border = 1;
        for (int x = gridMinX - border; x <= gridMaxX + border; x++) {
            for (int z = gridMinZ - border; z <= gridMaxZ + border; z++) {

                if (isGridCoordValid(x, z) && grid[x][z] != null && grid[x][z].isWalkable) {

                    // Clear old neighbors first
                    grid[x][z].neighbors.clear();

                    // Check North
                    if (isGridCoordValid(x, z - 1) && grid[x][z - 1] != null && grid[x][z - 1].isWalkable) {
                        grid[x][z].addNeighbor(grid[x][z - 1]);
                    }
                    // Check South
                    if (isGridCoordValid(x, z + 1) && grid[x][z + 1] != null && grid[x][z + 1].isWalkable) {
                        grid[x][z].addNeighbor(grid[x][z + 1]);
                    }
                    // Check West
                    if (isGridCoordValid(x - 1, z) && grid[x - 1][z] != null && grid[x - 1][z].isWalkable) {
                        grid[x][z].addNeighbor(grid[x - 1][z]);
                    }
                    // Check East
                    if (isGridCoordValid(x + 1, z) && grid[x + 1][z] != null && grid[x + 1][z].isWalkable) {
                        grid[x][z].addNeighbor(grid[x + 1][z]);
                    }
                }
            }
        }

        System.out.println("[PathfindingManager] Door area nodes set to walkable and neighbors rebuilt.");
    }
    // --- 💥💥💥 END FIX 💥💥💥 ---
}
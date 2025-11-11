package ohio.pugnetgames.chad.game;

// --- 徴FIX: Use our OWN PathNode class ---
import ohio.pugnetgames.chad.game.PathNode;
import java.util.List;
// --- 徴END FIX ---

// --- 徴FIX: Add imports that were missing ---
import ohio.pugnetgames.chad.game.World;
import ohio.pugnetgames.chad.game.GameObject;
// --- 徴END FIX ---

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering all 3D debug visualizations.
 * NEW: Now renders our DIY pathfinding nodes and lines.
 */
public class DebugRenderer {

    // --- 徴 NEW: How many nodes to draw around the player ---
    private static final int DEBUG_GRID_RADIUS = 30; // 30 nodes in each direction

    /**
     * Renders debug lines for AI pathing.
     * Assumes 3D View matrix is already set.
     *
     * @param isDebugLinesActive    Whether to draw debug lines at all.
     * @param isAutoCollectActive   Whether the AI is currently active.
     * @param player                The player object.
     * @param keyManager            The key manager.
     * @param world                 徴FIX: Added World object to get winTrigger
     * @param pathfinder            NEW: The pathfinding manager.
     */
    public void render(boolean isDebugLinesActive, boolean isAutoCollectActive,
                       Player player, KeyManager keyManager, World world, PathfindingManager pathfinder) {

        if (!isDebugLinesActive) return;

        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glLineWidth(2.0f);
        glPointSize(5.0f); // Make points visible

        // --- 1. AI "Dumb" Target Line (Cyan) ---
        // This line just points from the player to the *final* target
        if (isAutoCollectActive) {
            float targetX = 0, targetZ = 0, targetY = 1.5f;
            boolean hasTarget = false;

            Key nearestKey = keyManager.findNearestKey(player.getPosX(), player.getPosZ());
            if (nearestKey != null) {
                targetX = nearestKey.x;
                targetY = nearestKey.y;
                targetZ = nearestKey.z;
                hasTarget = true;
            } else if (keyManager.getKeysCollected() == keyManager.getTotalKeys() && world.getWinTrigger() != null) {
                // --- 徴FIX: This check now works! ---
                GameObject winTrigger = world.getWinTrigger();
                targetX = winTrigger.getPosX();
                targetY = winTrigger.getPosY();
                targetZ = winTrigger.getPosZ();
                hasTarget = true;
            }

            if (hasTarget) {
                glPushMatrix();
                glColor3f(0.0f, 1.0f, 1.0f); // Cyan line
                glBegin(GL_LINES);
                glVertex3f(player.getPosX(), player.getPosY(), player.getPosZ());
                glVertex3f(targetX, targetY, targetZ);
                glEnd();
                glPopMatrix();
            }
        }

        // --- 2. NEW: Render A* Path (Magenta Lines, Yellow Dots) ---
        if (pathfinder != null) {
            List<PathNode> path = pathfinder.getPath();
            if (path != null && !path.isEmpty()) {

                // Draw line segments (Magenta)
                glColor3f(1.0f, 0.0f, 1.0f);
                glBegin(GL_LINES);
                for (int i = 0; i < path.size() - 1; i++) {
                    PathNode a = path.get(i);
                    PathNode b = path.get(i + 1);
                    // Draw the line 0.5f unit above the ground
                    glVertex3f(a.worldX, 0.5f, a.worldZ);
                    glVertex3f(b.worldX, 0.5f, b.worldZ);
                }
                glEnd();

                // Draw nodes (Yellow)
                glColor3f(1.0f, 1.0f, 0.0f);
                glBegin(GL_POINTS);
                for (PathNode node : path) {
                    glVertex3f(node.worldX, 0.5f, node.worldZ);
                }
                glEnd();
            }

            // --- 3. 徴 MODIFIED: Render nodes NEAR THE PLAYER ---
            PathNode[][] grid = pathfinder.getGrid();
            // Make sure we have a grid AND a player to render around
            if (grid != null && player != null) {

                // Get player's grid position
                // (This assumes you made these methods public in PathfindingManager!)
                int playerGridX = pathfinder.worldToGridX(player.getPosX());
                int playerGridZ = pathfinder.worldToGridZ(player.getPosZ());

                glBegin(GL_POINTS);

                // Loop only in a box (radius) around the player
                for (int x = playerGridX - DEBUG_GRID_RADIUS; x <= playerGridX + DEBUG_GRID_RADIUS; x++) {
                    for (int z = playerGridZ - DEBUG_GRID_RADIUS; z <= playerGridZ + DEBUG_GRID_RADIUS; z++) {

                        // Make sure we're in bounds
                        // (This assumes you made this method public in PathfindingManager!)
                        if (!pathfinder.isGridCoordValid(x, z)) {
                            continue;
                        }

                        // If we are in bounds, get the node
                        PathNode node = grid[x][z];
                        if (node == null) continue;

                        // Draw the node
                        if (node.isWalkable) {
                            glColor3f(0.0f, 0.5f, 0.0f); // Dark green for walkable
                        } else {
                            glColor3f(0.5f, 0.0f, 0.0f); // Dark red for unwalkable
                        }
                        glVertex3f(node.worldX, 0.5f, node.worldZ); // Draw 0.5f above ground
                    }
                }
                glEnd();
            }
            // --- 徴 END MODIFICATION ---
        }
        // --- END NEW ---

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
    }
}
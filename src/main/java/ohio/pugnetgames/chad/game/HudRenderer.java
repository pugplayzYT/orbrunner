package ohio.pugnetgames.chad.game;

import java.util.List;
import static org.lwjgl.opengl.GL11.*;
import ohio.pugnetgames.chad.game.GameObject.ShapeType;
import ohio.pugnetgames.chad.game.Room.RoomType;

/**
 * Handles rendering all 2D overlay text (HUD).
 * NEW: Now renders objectives and dynamic messages.
 * MODIFIED: Now renders room floors on the minimap based explicitly on RoomType,
 * instead of checking floor object textures.
 */
public class HudRenderer {

    private FontRenderer fontRenderer;

    // --- 💥 NEW: Color Definitions for Room Types (RGB) 💥 ---
    private final float[] STANDARD_COLOR = {0.5f, 0.5f, 0.5f}; // Grey
    private final float[] COURTYARD_COLOR = {0.0f, 0.3f, 0.0f}; // Dark Green
    private final float[] BEDROOM_COLOR = {0.55f, 0.27f, 0.07f}; // Chocolate Brown
    private final float[] TUNNEL_COLOR = {0.4f, 0.4f, 0.4f}; // Slightly darker grey for tunnels
    // --- 💥 END NEW 💥 ---

    public void init() {
        fontRenderer = new FontRenderer();
        fontRenderer.init("BebasNeue-Regular.ttf");
    }

    /**
     * Renders all HUD elements.
     * Assumes 2D Ortho projection is already set.
     */
    // MODIFIED: Signature changed to accept isMapActive
    public void render(int width, int height, long keysCollected, long totalKeys, long bestWins,
                       boolean isFreeCamActive, boolean isAutoCollectActive,
                       boolean isDebugLinesActive, boolean adminPanelFeatureAvailable,
                       float horrorLevel, String objectiveText,
                       String popupMessage, float popupMessageTimer, String hotColdText,
                       // --- NEW ARGUMENTS FOR MINIMAP ---
                       PathfindingManager pathfinder, Player player, KeyManager keyManager, GameObject winTrigger, World world, boolean isMapActive) { // ADDED isMapActive

        // --- 💥 MODIFIED: Only render normal HUD elements if map is NOT full screen 💥 ---
        if (!isMapActive) {
            String scoreText = "KEYS: " + keysCollected + " / " + totalKeys; // MODIFIED
            String highscoreText = "BEST WINS: " + bestWins; // This is still correct
            // MODIFIED: Removed timerText

            // --- Standard HUD ---
            fontRenderer.drawText(scoreText, 20, 10, 1.0f, 1.0f, 0.0f);
            fontRenderer.drawText(highscoreText, 20, 60, 0.8f, 0.8f, 0.8f);

            // --- NEW: Draw Objective ---
            if (objectiveText != null && !objectiveText.isEmpty()) {
                fontRenderer.drawText(objectiveText, 20, 110, 0.9f, 0.9f, 0.9f);
            }
            // --- END NEW ---

            // MODIFIED: Removed timer drawing logic

            // --- Draw Statuses ---
            if (isFreeCamActive) {
                String freeCamText = "FREE CAM ACTIVE";
                float freeCamX = (width / 2.0f) - (freeCamText.length() * 10);
                fontRenderer.drawText(freeCamText, freeCamX, 10, 0.0f, 1.0f, 1.0f);
            } else if (isAutoCollectActive) {
                String autoCollectText = "AUTO COLLECT ACTIVE";
                float autoCollectX = (width / 2.0f) - (autoCollectText.length() * 10);
                fontRenderer.drawText(autoCollectText, autoCollectX, 10, 1.0f, 0.5f, 0.0f);
            }

            if (isDebugLinesActive) {
                String debugText = "DEBUG LINES ON";
                float debugX = (width / 2.0f) - (debugText.length() * 10);
                fontRenderer.drawText(debugText, debugX, 60, 0.0f, 1.0f, 1.0f);

                // --- NEW: Draw Horror Level ---
                String horrorText = String.format("HORROR: %.0f%%", horrorLevel);
                float horrorX = (width / 2.0f) - (horrorText.length() * 10);
                // Draw it below the "DEBUG LINES ON" text, in red
                fontRenderer.drawText(horrorText, horrorX, 110, 1.0f, 0.0f, 0.0f);
                // --- END NEW ---
            }

            // --- NEW: Draw Hot/Cold Text ---
            if (hotColdText != null && !hotColdText.isEmpty()) {
                // Determine color based on text
                float r = 1.0f, g = 1.0f, b = 1.0f; // Default white
                if (hotColdText.equals("Burning Hot!")) { r = 1.0f; g = 0.0f; b = 0.0f; } // Red
                else if (hotColdText.equals("Hot")) { r = 1.0f; g = 0.5f; b = 0.0f; } // Orange
                else if (hotColdText.equals("Warm")) { r = 1.0f; g = 1.0f; b = 0.0f; } // Yellow
                else if (hotColdText.equals("Cold")) { r = 0.0f; g = 1.0f; b = 1.0f; } // Cyan
                else if (hotColdText.equals("Freezing")) { r = 0.0f; g = 0.0f; b = 1.0f; } // Blue

                // Center it near the bottom
                float hotColdX = (width / 2.0f) - (hotColdText.length() * 10);
                float hotColdY = height - 80;
                fontRenderer.drawText(hotColdText, hotColdX, hotColdY, r, g, b);
            }
            // --- END NEW ---

            // --- NEW: Draw Popup Message ---
            // (Draw this *after* hot/cold so it's on top if they overlap)
            if (popupMessageTimer > 0 && popupMessage != null && !popupMessage.isEmpty()) {
                // Center it on screen, in red
                float popupX = (width / 2.0f) - (popupMessage.length() * 10);
                float popupY = (height / 2.0f);
                fontRenderer.drawText(popupMessage, popupX, popupY, 1.0f, 0.0f, 0.0f);
            }
            // --- END NEW ---


            if (adminPanelFeatureAvailable) {
                String f2Hint = "Press F2 for Admin Panel";
                float f2HintX = width - (f2Hint.length() * 15) - 20;
                fontRenderer.drawText(f2Hint, f2HintX, height - 40, 0.8f, 0.8f, 0.8f);
            }

            // --- 💥 NEW: Controls Display ---
            float controlX = 20;
            float controlY = height - 230;
            float lineHeight = 30; // Spacing

            fontRenderer.drawText("Controls:", controlX, controlY, 1.0f, 1.0f, 1.0f);
            controlY += lineHeight;
            fontRenderer.drawText("WASD: Move", controlX, controlY, 0.8f, 0.8f, 0.8f);
            controlY += lineHeight;
            fontRenderer.drawText("Space: Jump", controlX, controlY, 0.8f, 0.8f, 0.8f);
            controlY += lineHeight;
            fontRenderer.drawText("Shift: Sprint", controlX, controlY, 0.8f, 0.8f, 0.8f);
            controlY += lineHeight;
            fontRenderer.drawText("Mouse: Look", controlX, controlY, 0.8f, 0.8f, 0.8f);
            controlY += lineHeight;
            fontRenderer.drawText("ESC: Quit", controlX, controlY, 0.8f, 0.8f, 0.8f);
            controlY += lineHeight; // Add M key hint to the bottom left controls
            fontRenderer.drawText("M: Map", controlX, controlY, 0.8f, 0.8f, 0.8f);
            // --- 💥 END NEW ---
        }

        // --- 💥 NEW: Draw Mini Map (Top Right) 💥 ---
        // Only draw the map if the pathfinder is available (i.e., game is running) OR if it's the full-screen map
        if (pathfinder != null && world != null) {
            renderMiniMap(width, height, pathfinder, player, keyManager, winTrigger, world, isMapActive);
        }
        // --- 💥 END NEW 💥 ---
    }

    /**
     * --- NEW: Helper method to map RoomType to its minimap color ---
     */
    private float[] getColorForRoomType(Room.RoomType type) {
        switch (type) {
            case STANDARD:
                return STANDARD_COLOR;
            case COURTYARD:
                return COURTYARD_COLOR;
            case BEDROOM:
                return BEDROOM_COLOR;
            default:
                return STANDARD_COLOR;
        }
    }


    /**
     * Renders a 2D representation of the maze by drawing scaled quads for
     * each major structural GameObject (walls and floors).
     */
    private void renderMiniMap(int screenWidth, int screenHeight, PathfindingManager pathfinder,
                               Player player, KeyManager keyManager, GameObject winTrigger, World world, boolean isFullScreen) {

        // Guards
        float wMinX = pathfinder.getWorldMinX();
        float wMinZ = pathfinder.getWorldMinZ();
        float wMaxX = pathfinder.getWorldMaxX();
        float wMaxZ = pathfinder.getWorldMaxZ();

        if (wMaxX - wMinX <= 0 || wMaxZ - wMinZ <= 0) return;

        // Mini Map Constants
        final int CORNER_MAP_SIZE = 300;
        final int FULL_MAP_SIZE = (int) (Math.min(screenWidth, screenHeight) * 0.9f);
        final int MAP_PADDING = 20;
        final float PLAYER_SIZE = isFullScreen ? 10.0f : 7.0f;
        // 💥 REMOVED KEY_SIZE/WIN_SIZE CONSTANTS

        // --- 💥 MODIFIED: Calculate dynamic size and position 💥 ---
        int mapSize = isFullScreen ? FULL_MAP_SIZE : CORNER_MAP_SIZE;

        float mapX_Screen, mapY_Screen;
        if (isFullScreen) {
            mapX_Screen = (screenWidth - mapSize) / 2.0f;
            mapY_Screen = (screenHeight - mapSize) / 2.0f;
        } else {
            mapX_Screen = screenWidth - mapSize - MAP_PADDING;
            mapY_Screen = MAP_PADDING;
        }
        // --- 💥 END MODIFIED 💥 ---

        // World dimensions
        float worldWidth = wMaxX - wMinX;
        float worldDepth = wMaxZ - wMinZ;

        // Calculate scaling ratio (pixels per world unit)
        float mapRatio = mapSize / Math.max(worldWidth, worldDepth);

        // --- Setup GL State for 2D Drawing ---
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Draw Map Background (Dark Gray)
        glColor4f(0.1f, 0.1f, 0.1f, isFullScreen ? 1.0f : 0.8f); // Solid background when full screen
        glBegin(GL_QUADS);
        glVertex2f(mapX_Screen, mapY_Screen);
        glVertex2f(mapX_Screen + mapSize, mapY_Screen);
        glVertex2f(mapX_Screen + mapSize, mapY_Screen + mapSize);
        glVertex2f(mapX_Screen, mapY_Screen + mapSize);
        glEnd();

        // 1. 💥 DRAW TUNNEL FLOORS FIRST (Layer 1 - Bottom) 💥
        // This draws the generic grey tunnels, which will be covered by the room quads.
        glBegin(GL_QUADS);
        float[] tunnelColor = TUNNEL_COLOR;
        glColor3f(tunnelColor[0], tunnelColor[1], tunnelColor[2]);

        synchronized (world.getStaticObjects()) {
            for (GameObject obj : world.getStaticObjects()) {
                ShapeType shape = obj.getShape();

                // A Tunnel floor is a CUBE with low Y-scale AND the wallTextureID
                boolean isTunnelFloor = (shape == ShapeType.CUBE && obj.getScaleY() <= 0.5f && obj.getTextureID() == world.getWallTextureID());

                if (isTunnelFloor) {
                    // Calculate Bounding Box of Object in World Space
                    float oMinX_World = obj.getPosX() - obj.getScaleX() / 2.0f;
                    float oMaxX_World = obj.getPosX() + obj.getScaleX() / 2.0f;
                    float oMinZ_World = obj.getPosZ() - obj.getScaleZ() / 2.0f;
                    float oMaxZ_World = obj.getPosZ() + obj.getScaleZ() / 2.0f;

                    // Convert to Screen Space
                    float oMinX_Screen = mapX_Screen + (oMinX_World - wMinX) * mapRatio;
                    float oMaxX_Screen = mapX_Screen + (oMaxX_World - wMinX) * mapRatio;
                    float oMinZ_Screen = mapY_Screen + (oMinZ_World - wMinZ) * mapRatio; // Z maps to screen Y
                    float oMaxZ_Screen = mapY_Screen + (oMaxZ_World - wMinZ) * mapRatio;

                    // Draw Quad
                    glVertex2f(oMinX_Screen, oMinZ_Screen);
                    glVertex2f(oMaxX_Screen, oMinZ_Screen);
                    glVertex2f(oMaxX_Screen, oMaxZ_Screen);
                    glVertex2f(oMinX_Screen, oMaxZ_Screen);
                }
            }
        }
        glEnd();


        // 2. 💥 DRAW ROOM FLOORS LAST (Layer 2 - Middle/Top) 💥
        // This overwrites the tunnel color where rooms are defined.
        glBegin(GL_QUADS);
        // Iterate over all rooms
        for (Room room : world.getAllRooms()) {
            float[] color = getColorForRoomType(room.getType());
            glColor3f(color[0], color[1], color[2]);

            // Calculate Bounding Box of Room in World Space
            float oMinX_World = room.minX;
            float oMaxX_World = room.maxX;
            float oMinZ_World = room.minZ;
            float oMaxZ_World = room.maxZ;

            // Convert to Screen Space
            float oMinX_Screen = mapX_Screen + (oMinX_World - wMinX) * mapRatio;
            float oMaxX_Screen = mapX_Screen + (oMaxX_World - wMinX) * mapRatio;
            float oMinZ_Screen = mapY_Screen + (oMinZ_World - wMinZ) * mapRatio; // Z maps to screen Y
            float oMaxZ_Screen = mapY_Screen + (oMaxZ_World - wMinZ) * mapRatio;

            // Draw Quad
            glVertex2f(oMinX_Screen, oMinZ_Screen);
            glVertex2f(oMaxX_Screen, oMinZ_Screen);
            glVertex2f(oMaxX_Screen, oMaxZ_Screen);
            glVertex2f(oMinX_Screen, oMaxZ_Screen);
        }
        glEnd();


        // 3. Draw World Geometry (Walls and Furniture - Layer 3 - Top)
        synchronized (world.getStaticObjects()) {
            for (GameObject obj : world.getStaticObjects()) {
                // Only draw structural elements that should block the view (walls, tables, beds)
                ShapeType shape = obj.getShape();
                if (shape != ShapeType.CUBE && shape != ShapeType.PLANE) continue;

                // --- Determine if object is a Wall/Obstacle or a Floor (to skip it) ---
                boolean isFloor = (shape == ShapeType.PLANE) || // Skip Plane floors (already drawn by room loop)
                        (shape == ShapeType.CUBE && obj.getScaleY() <= 0.5f); // Skip low CUBE floors (already drawn by tunnel loop)

                if (isFloor) continue; // Skip all floor objects

                // --- Walls/Obstacles logic ---
                // If it's collidable AND tall, it's a wall or furniture
                // We use sheetsTextureID to skip the mattress which is floor height
                if (!obj.isCollidable() || obj.getScaleY() > 5.0f || obj.getTextureID() == world.getSheetsTextureID()) {
                    continue; // Skip invisible walls, triggers, and mattresses (sheets)
                }

                // Wall/Obstacle Color (Red)
                glColor3f(0.8f, 0.1f, 0.1f);

                // --- Calculate Bounding Box of Object in World Space ---
                float oMinX_World = obj.getPosX() - obj.getScaleX() / 2.0f;
                float oMaxX_World = obj.getPosX() + obj.getScaleX() / 2.0f;
                float oMinZ_World = obj.getPosZ() - obj.getScaleZ() / 2.0f;
                float oMaxZ_World = obj.getPosZ() + obj.getScaleZ() / 2.0f;

                // --- Convert to Screen Space ---
                float oMinX_Screen = mapX_Screen + (oMinX_World - wMinX) * mapRatio;
                float oMaxX_Screen = mapX_Screen + (oMaxX_World - wMinX) * mapRatio;
                float oMinZ_Screen = mapY_Screen + (oMinZ_World - wMinZ) * mapRatio; // Z maps to screen Y
                float oMaxZ_Screen = mapY_Screen + (oMaxZ_World - wMinZ) * mapRatio;

                // Draw Quad
                glBegin(GL_QUADS);
                glVertex2f(oMinX_Screen, oMinZ_Screen);
                glVertex2f(oMaxX_Screen, oMinZ_Screen);
                glVertex2f(oMaxX_Screen, oMaxZ_Screen);
                glVertex2f(oMinX_Screen, oMaxZ_Screen);
                glEnd();
            }
        }

        // 4. Draw Player Location (Red Circle/Point)
        glPointSize(PLAYER_SIZE);
        glBegin(GL_POINTS); // Restart points drawing after removing other sections

        float pX_Screen = mapX_Screen + (player.getPosX() - wMinX) * mapRatio;
        float pZ_Screen = mapY_Screen + (player.getPosZ() - wMinZ) * mapRatio;

        glColor3f(1.0f, 0.0f, 0.0f); // Red
        glVertex2f(pX_Screen, pZ_Screen);

        glEnd();
        glPointSize(1.0f); // Reset point size

        // --- Cleanup GL State ---
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
    }

    public void cleanup() {
        if (fontRenderer != null) {
            fontRenderer.cleanup();
        }
    }
}
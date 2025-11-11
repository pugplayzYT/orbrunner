package ohio.pugnetgames.chad.game;

import java.util.List;
import static org.lwjgl.opengl.GL11.*;
import ohio.pugnetgames.chad.game.GameObject.ShapeType;
import ohio.pugnetgames.chad.game.Room.RoomType;

/**
 * Handles rendering all 2D overlay text (HUD).
 * NEW: Now renders objectives and dynamic messages.
 */
public class HudRenderer {

    private FontRenderer fontRenderer;

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

    // --- 💥 MODIFIED METHOD: renderMiniMap (Removed Keys/Exit Logic) 💥 ---
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

        // 1. Draw World Geometry (Quads for floors and walls)
        // !!!!!!!!!!!!!! THE FIX !!!!!!!!!!!!!!
        // We must lock the list we are iterating over to prevent a crash
        // if the admin panel adds an object at the same time.
        synchronized (world.getStaticObjects()) {
            for (GameObject obj : world.getStaticObjects()) {
                // Only draw major structural elements
                ShapeType shape = obj.getShape();
                if (shape != ShapeType.CUBE && shape != ShapeType.PLANE) continue;

                // Only consider floor objects (low Y-scale) and tall objects (walls)
                boolean isFloor = shape == ShapeType.PLANE || obj.getScaleY() <= 0.5f;

                // --- Calculate Bounding Box of Object in World Space ---
                float oMinX_World = obj.getPosX() - obj.getScaleX() / 2.0f;
                float oMaxX_World = obj.getPosX() + obj.getScaleX() / 2.0f;
                float oMinZ_World = obj.getPosZ() - obj.getScaleZ() / 2.0f;
                float oMaxZ_World = obj.getPosZ() + obj.getScaleZ() / 2.0f;

                // --- Convert to Screen Space ---
                // x_screen = mapX_Screen + (x_world - wMinX) * mapRatio
                float oMinX_Screen = mapX_Screen + (oMinX_World - wMinX) * mapRatio;
                float oMaxX_Screen = mapX_Screen + (oMaxX_World - wMinX) * mapRatio;
                float oMinZ_Screen = mapY_Screen + (oMinZ_World - wMinZ) * mapRatio; // Z maps to screen Y
                float oMaxZ_Screen = mapY_Screen + (oMaxZ_World - wMinZ) * mapRatio;

                // Set Color
                if (isFloor) {
                    // Courtyard floors (orb/grass texture)
                    if (obj.getTextureID() == world.getOrbTextureID()) {
                        glColor3f(0.0f, 0.3f, 0.0f); // Dark Green
                    } else {
                        glColor3f(0.5f, 0.5f, 0.5f); // Tunnel/Room floor (Gray)
                    }
                } else {
                    // Walls/Tables (Collidable, tall objects)
                    if (!obj.isCollidable() || obj.getScaleY() > 5.0f) {
                        continue; // Skip invisible walls/triggers
                    }
                    glColor3f(0.8f, 0.1f, 0.1f); // Walls (Red)
                }

                // Draw Quad
                glBegin(GL_QUADS);
                glVertex2f(oMinX_Screen, oMinZ_Screen);
                glVertex2f(oMaxX_Screen, oMinZ_Screen);
                glVertex2f(oMaxX_Screen, oMaxZ_Screen);
                glVertex2f(oMinX_Screen, oMaxZ_Screen);
                glEnd();
            }
        } // !!!!!!!!!!!!!! END OF THE FIX (close synchronized block) !!!!!!!!!!!!!!

        // 💥 REMOVED: 2. Draw Key Locations (Yellow Circles/Points)
        /*
        glPointSize(KEY_SIZE);
        glBegin(GL_POINTS);
        for (Key key : keyManager.getKeys()) {
            if (!key.collected) {
                float kX_Screen = mapX_Screen + (key.x - wMinX) * mapRatio;
                float kZ_Screen = mapY_Screen + (key.z - wMinZ) * mapRatio;

                glColor3f(1.0f, 1.0f, 0.0f); // Yellow
                glVertex2f(kX_Screen, kZ_Screen);
            }
        }
        */

        // 💥 REMOVED: 3. Draw Win Trigger Location (Bright Green Circle/Point)
        /*
        if (winTrigger != null && keyManager.getKeysCollected() == keyManager.getTotalKeys()) {
             glPointSize(WIN_SIZE);
             float wX_Screen = mapX_Screen + (winTrigger.getPosX() - wMinX) * mapRatio;
             float wZ_Screen = mapY_Screen + (winTrigger.getPosZ() - wMinZ) * mapRatio;

             glColor3f(0.0f, 1.0f, 0.0f); // Bright Green
             glVertex2f(wX_Screen, wZ_Screen);
        }
        */

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
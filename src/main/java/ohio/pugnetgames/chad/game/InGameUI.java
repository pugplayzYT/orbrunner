package ohio.pugnetgames.chad.game;

import ohio.pugnetgames.chad.core.Difficulty;
import ohio.pugnetgames.chad.core.ScoreManager;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * InGameUI — Renders ALL menus and panels directly in the OpenGL window.
 * Replaces Swing-based MainMenu, AdminPanelUI, and JOptionPane dialogs.
 *
 * Handles: Main Menu, Pause Menu, Admin Panel, Game Over screen.
 * Draws buttons as colored quads with text, handles mouse hover/click.
 */
public class InGameUI {

    // ============================================================
    // INNER TYPES
    // ============================================================

    /** Simple rectangle for button hit-testing. */
    private static class Rect {
        float x, y, w, h;

        Rect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    /** A clickable UI button. */
    private static class Button {
        String label;
        Rect rect;
        Runnable action;
        boolean hovered;

        float borderR, borderG, borderB;

        Button(String label, float x, float y, float w, float h,
                float bR, float bG, float bB, Runnable action) {
            this.label = label;
            this.rect = new Rect(x, y, w, h);
            this.borderR = bR;
            this.borderG = bG;
            this.borderB = bB;
            this.action = action;
            this.hovered = false;
        }
    }

    // ============================================================
    // STATE
    // ============================================================

    private FontRenderer fontRenderer;

    // --- Main Menu ---
    private final List<Button> menuButtons = new ArrayList<>();
    private Difficulty selectedDifficulty = null;
    private boolean quitRequested = false;

    // --- Game Over Overlay ---
    private final List<Button> gameOverButtons = new ArrayList<>();
    private String gameOverMessage = "";
    private long gameOverWins = 0;
    private boolean gameOverReturnToMenu = false;

    // --- Pause Menu ---
    private final List<Button> pauseButtons = new ArrayList<>();
    private boolean pauseResumeRequested = false;
    private boolean pauseQuitToMenuRequested = false;

    // Pause menu settings (read/written by GamePanel)
    private float sensitivity = 0.1f; // Mouse sensitivity (0.01 - 0.5)
    private float fieldOfView = 60.0f; // FOV (30 - 120)
    private float fogDensity = 0.07f; // Fog density (0.0 - 0.2)
    private boolean invertY = false; // Invert Y axis
    private float masterVolume = 1.0f; // Master volume (0.0 - 1.0)

    // --- Admin Panel (in-game overlay) ---
    private boolean adminPanelOpen = false;
    private boolean adminPanelJustClosed = false; // flag for GamePanel to detect close-button usage
    private final List<Button> adminButtons = new ArrayList<>();

    // Admin panel state mirrors
    private boolean adminAutoCollect = false;
    private boolean adminDebugLines = false;
    private boolean adminDebugAvailable = false;
    private float adminGroundR = 0.1f, adminGroundG = 0.5f, adminGroundB = 0.2f;
    private float[] adminPlayerPos = { 0, 0, 0 };
    private String adminSelectedShape = "CUBE";
    private String adminSelectedTexture = "NONE (Color)";
    private List<String> adminShapeOptions = Arrays.asList("CUBE", "SPHERE", "TABLE", "KEY", "BED", "TREE",
            "ESCAPE_DOOR");
    private List<String> adminTextureOptions = new ArrayList<>();
    private int adminShapeIndex = 0;
    private int adminTextureIndex = 0;

    // Teleport input
    private float tpX = 0, tpY = 1.5f, tpZ = 0;

    // Mouse state
    private double mouseX, mouseY;
    private boolean mousePressed = false;
    private boolean mouseJustClicked = false;

    // Screen dimensions
    private int screenW, screenH;

    // ============================================================
    // INIT
    // ============================================================

    public void init() {
        fontRenderer = new FontRenderer();
        fontRenderer.init("BebasNeue-Regular.ttf");
    }

    // ============================================================
    // MOUSE INPUT (called from GamePanel's GLFW callbacks)
    // ============================================================

    public void onMouseMove(double x, double y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    public void onMouseButton(int button, int action) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW_PRESS) {
                mousePressed = true;
                mouseJustClicked = true;
            } else if (action == GLFW_RELEASE) {
                mousePressed = false;
            }
        }
    }

    // ============================================================
    // SETTINGS GETTERS (for GamePanel to read)
    // ============================================================

    public float getSensitivity() {
        return sensitivity;
    }

    public float getFieldOfView() {
        return fieldOfView;
    }

    public float getFogDensity() {
        return fogDensity;
    }

    public boolean isInvertY() {
        return invertY;
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public void setSensitivity(float s) {
        this.sensitivity = s;
    }

    public void setFieldOfView(float f) {
        this.fieldOfView = f;
    }

    public void setFogDensity(float d) {
        this.fogDensity = d;
    }

    // ============================================================
    // MAIN MENU
    // ============================================================

    public Difficulty getSelectedDifficulty() {
        Difficulty d = selectedDifficulty;
        selectedDifficulty = null;
        return d;
    }

    public boolean isQuitRequested() {
        boolean q = quitRequested;
        quitRequested = false;
        return q;
    }

    public void renderMainMenu(int width, int height) {
        this.screenW = width;
        this.screenH = height;

        // Background
        drawFilledRect(0, 0, width, height, 0.094f, 0.102f, 0.125f, 1.0f);

        // Subtle gradient overlay at top
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        drawFilledRect(0, 0, width, height / 3, 0.15f, 0.08f, 0.25f, 0.3f);

        // Title
        String title = "MAZE ESCAPE 3D";
        float titleX = (width / 2.0f) - (title.length() * 14);
        float titleY = height * 0.15f;
        fontRenderer.drawText(title, titleX - 2, titleY - 2, 0.0f, 0.6f, 0.2f);
        fontRenderer.drawText(title, titleX, titleY, 0.235f, 1.0f, 0.47f);

        // Subtitle
        String subtitle = "LWJGL OPENGL EDITION";
        float subX = (width / 2.0f) - (subtitle.length() * 8);
        fontRenderer.drawText(subtitle, subX, titleY + 60, 0.6f, 0.6f, 0.6f);

        // Best score
        long bestScore = ScoreManager.loadBestScore();
        String bestText = "BEST WINS: " + bestScore;
        float bestX = (width / 2.0f) - (bestText.length() * 10);
        fontRenderer.drawText(bestText, bestX, titleY + 110, 0.8f, 0.8f, 0.8f);

        // Buttons
        menuButtons.clear();
        float btnW = 360;
        float btnH = 55;
        float btnX = (width - btnW) / 2.0f;
        float btnStartY = height * 0.45f;
        float btnGap = 75;

        menuButtons.add(new Button("EASY (3 Keys, +1 Win)", btnX, btnStartY, btnW, btnH,
                0.235f, 1.0f, 0.47f,
                () -> selectedDifficulty = Difficulty.EASY));

        menuButtons.add(new Button("HARD (10 Keys, +5 Wins)", btnX, btnStartY + btnGap, btnW, btnH,
                1.0f, 0.4f, 0.2f,
                () -> selectedDifficulty = Difficulty.HARD));

        menuButtons.add(new Button("QUIT", btnX, btnStartY + btnGap * 2, btnW, btnH,
                0.6f, 0.6f, 0.6f,
                () -> quitRequested = true));

        processButtons(menuButtons);

        // Footer
        String footer = "WASD to move  |  Mouse to look  |  ESC for pause menu";
        float footX = (width / 2.0f) - (footer.length() * 7);
        fontRenderer.drawText(footer, footX, height - 60, 0.4f, 0.4f, 0.4f);

        consumeClick();
    }

    // ============================================================
    // PAUSE MENU
    // ============================================================

    public boolean isPauseResumeRequested() {
        boolean r = pauseResumeRequested;
        pauseResumeRequested = false;
        return r;
    }

    public boolean isPauseQuitToMenuRequested() {
        boolean r = pauseQuitToMenuRequested;
        pauseQuitToMenuRequested = false;
        return r;
    }

    public void renderPauseMenu(int width, int height) {
        this.screenW = width;
        this.screenH = height;

        // Darken background
        drawFilledRect(0, 0, width, height, 0.0f, 0.0f, 0.0f, 0.7f);

        // Panel
        float panelW = 500;
        float panelH = 580;
        float panelX = (width - panelW) / 2.0f;
        float panelY = (height - panelH) / 2.0f;
        drawFilledRect(panelX, panelY, panelW, panelH, 0.094f, 0.102f, 0.125f, 0.96f);
        drawOutlineRect(panelX, panelY, panelW, panelH, 0.235f, 1.0f, 0.47f);

        float cx = panelX + 30;
        float cy = panelY + 25;
        float contentW = panelW - 60;
        float btnH = 45;

        // Title
        String title = "PAUSED";
        float titleX = (width / 2.0f) - (title.length() * 14);
        fontRenderer.drawText(title, titleX, cy, 0.235f, 1.0f, 0.47f);
        cy += 55;

        // Resume button
        pauseButtons.clear();
        pauseButtons.add(new Button("RESUME", cx, cy, contentW, btnH,
                0.235f, 1.0f, 0.47f,
                () -> pauseResumeRequested = true));
        cy += 65;

        // --- Settings Section ---
        fontRenderer.drawText("--- Settings ---", cx, cy, 0.8f, 0.6f, 1.0f);
        cy += 40;

        // Sensitivity slider (0.01 - 0.5, display as percentage-ish)
        float sensNorm = (sensitivity - 0.01f) / (0.5f - 0.01f); // normalize to 0-1
        cy = renderSlider(cx, cy, contentW, "Sensitivity", sensNorm, 0.8f, 0.6f, 1.0f, (val) -> {
            sensitivity = 0.01f + val * (0.5f - 0.01f);
        });
        // Show actual value
        fontRenderer.drawText(String.format("  (%.2f)", sensitivity), cx + contentW - 60, cy - 35, 0.6f, 0.6f, 0.6f);

        // FOV slider (30 - 120)
        float fovNorm = (fieldOfView - 30.0f) / (120.0f - 30.0f);
        cy = renderSlider(cx, cy, contentW, "FOV", fovNorm, 1.0f, 0.8f, 0.3f, (val) -> {
            fieldOfView = 30.0f + val * (120.0f - 30.0f);
        });
        fontRenderer.drawText(String.format("  (%.0f)", fieldOfView), cx + contentW - 60, cy - 35, 0.6f, 0.6f, 0.6f);

        // Fog density slider (0.0 - 0.2)
        float fogNorm = fogDensity / 0.2f;
        cy = renderSlider(cx, cy, contentW, "Fog", fogNorm, 0.5f, 0.7f, 1.0f, (val) -> {
            fogDensity = val * 0.2f;
        });
        fontRenderer.drawText(String.format("  (%.3f)", fogDensity), cx + contentW - 80, cy - 35, 0.6f, 0.6f, 0.6f);

        // Volume slider (0.0 - 1.0)
        cy = renderSlider(cx, cy, contentW, "Vol", masterVolume, 0.3f, 1.0f, 0.5f, (val) -> {
            masterVolume = val;
        });

        cy += 5;

        // --- Fun Toggles ---
        fontRenderer.drawText("--- Extras ---", cx, cy, 1.0f, 0.65f, 0.0f);
        cy += 40;

        // Invert Y toggle
        String invText = invertY ? "Invert Y: ON" : "Invert Y: OFF";
        pauseButtons.add(new Button(invText, cx, cy, contentW, 38,
                invertY ? 1.0f : 0.5f, invertY ? 0.65f : 0.5f, invertY ? 0.0f : 0.5f,
                () -> invertY = !invertY));
        cy += 55;

        // Quit to Menu button (red-ish)
        pauseButtons.add(new Button("QUIT TO MENU", cx, cy, contentW, btnH,
                1.0f, 0.3f, 0.3f,
                () -> pauseQuitToMenuRequested = true));

        processButtons(pauseButtons);

        // Hint at bottom
        String hint = "Press ESC to resume";
        float hintX = (width / 2.0f) - (hint.length() * 8);
        fontRenderer.drawText(hint, hintX, panelY + panelH - 35, 0.4f, 0.4f, 0.4f);

        consumeClick();
    }

    // ============================================================
    // GAME OVER OVERLAY
    // ============================================================

    public void setGameOverState(String message, long totalWins) {
        this.gameOverMessage = message;
        this.gameOverWins = totalWins;
        this.gameOverReturnToMenu = false;
    }

    public boolean isGameOverReturnToMenu() {
        boolean r = gameOverReturnToMenu;
        gameOverReturnToMenu = false;
        return r;
    }

    public void renderGameOver(int width, int height) {
        this.screenW = width;
        this.screenH = height;

        // Darken background
        drawFilledRect(0, 0, width, height, 0.0f, 0.0f, 0.0f, 0.75f);

        // Panel
        float panelW = 500;
        float panelH = 300;
        float panelX = (width - panelW) / 2.0f;
        float panelY = (height - panelH) / 2.0f;
        drawFilledRect(panelX, panelY, panelW, panelH, 0.12f, 0.13f, 0.16f, 0.95f);
        drawOutlineRect(panelX, panelY, panelW, panelH, 0.235f, 1.0f, 0.47f);

        String goTitle = "GAME OVER";
        float goTitleX = (width / 2.0f) - (goTitle.length() * 14);
        fontRenderer.drawText(goTitle, goTitleX, panelY + 30, 1.0f, 0.3f, 0.3f);

        float msgX = panelX + 30;
        fontRenderer.drawText(gameOverMessage, msgX, panelY + 90, 0.9f, 0.9f, 0.9f);

        String winsText = "Total Wins: " + gameOverWins;
        fontRenderer.drawText(winsText, msgX, panelY + 140, 0.8f, 0.8f, 0.8f);

        gameOverButtons.clear();
        float btnW = 250;
        float btnH = 50;
        float btnX = (width - btnW) / 2.0f;
        float btnY = panelY + panelH - 80;
        gameOverButtons.add(new Button("RETURN TO MENU", btnX, btnY, btnW, btnH,
                0.235f, 1.0f, 0.47f,
                () -> gameOverReturnToMenu = true));

        processButtons(gameOverButtons);
        consumeClick();
    }

    // ============================================================
    // ADMIN PANEL (in-game overlay)
    // ============================================================

    public boolean isAdminPanelOpen() {
        return adminPanelOpen;
    }

    public void toggleAdminPanel() {
        adminPanelOpen = !adminPanelOpen;
    }

    public void closeAdminPanel() {
        adminPanelOpen = false;
    }

    /**
     * Returns true if the admin panel was just closed via the CLOSE button
     * (as opposed to F2 key). GamePanel checks this to restore cursor/movement.
     */
    public boolean wasAdminPanelJustClosed() {
        boolean v = adminPanelJustClosed;
        adminPanelJustClosed = false;
        return v;
    }

    /** Callback interface for admin actions. */
    public interface AdminCallback {
        void onToggleAutoCollect(boolean active);

        void onToggleDebugLines(boolean active);

        void onSetGroundColor(float r, float g, float b);

        void onSpawnObject(String shape, String textureName);

        void onTeleport(float x, float y, float z);

        void onCopyCoords();
    }

    public void syncAdminState(boolean autoCollect, boolean debugLines, boolean debugAvailable,
            float gR, float gG, float gB, float[] playerPos,
            Map<String, Integer> textures) {
        this.adminAutoCollect = autoCollect;
        this.adminDebugLines = debugLines;
        this.adminDebugAvailable = debugAvailable;
        this.adminGroundR = gR;
        this.adminGroundG = gG;
        this.adminGroundB = gB;
        if (playerPos != null) {
            this.adminPlayerPos = playerPos;
            this.tpX = playerPos[0];
            this.tpY = playerPos[1];
            this.tpZ = playerPos[2];
        }
        adminTextureOptions.clear();
        adminTextureOptions.add("NONE (Color)");
        if (textures != null) {
            List<String> names = new ArrayList<>(textures.keySet());
            Collections.sort(names);
            adminTextureOptions.addAll(names);
        }
        if (adminShapeIndex >= adminShapeOptions.size())
            adminShapeIndex = 0;
        if (adminTextureIndex >= adminTextureOptions.size())
            adminTextureIndex = 0;
    }

    public void renderAdminPanel(int width, int height, AdminCallback callback) {
        if (!adminPanelOpen)
            return;

        this.screenW = width;
        this.screenH = height;

        float panelW = 380;
        float panelH = 700;
        float panelX = width - panelW - 20;
        float panelY = 20;

        if (panelH > height - 40)
            panelH = height - 40;

        drawFilledRect(panelX, panelY, panelW, panelH, 0.094f, 0.102f, 0.125f, 0.93f);
        drawOutlineRect(panelX, panelY, panelW, panelH, 0.235f, 1.0f, 0.47f);

        adminButtons.clear();
        float cx = panelX + 15;
        float cy = panelY + 15;
        float contentW = panelW - 30;
        float btnH = 36;
        float lineH = 42;

        // Title
        fontRenderer.drawText("PUG'S ADMIN PANEL", cx, cy, 0.235f, 1.0f, 0.47f);
        cy += 45;

        // --- Section: Spawn Object ---
        fontRenderer.drawText("--- Spawn Object ---", cx, cy, 1.0f, 0.65f, 0.0f);
        cy += 35;

        // Shape selector
        adminSelectedShape = adminShapeOptions.get(adminShapeIndex);
        fontRenderer.drawText("Shape: " + adminSelectedShape, cx, cy, 0.8f, 0.8f, 0.8f);
        float smallBtnW = 40;
        float selectorBtnY = cy - 5;
        adminButtons.add(new Button("<", cx + contentW - smallBtnW * 2 - 10, selectorBtnY, smallBtnW, 30,
                1.0f, 0.65f, 0.0f,
                () -> {
                    adminShapeIndex--;
                    if (adminShapeIndex < 0)
                        adminShapeIndex = adminShapeOptions.size() - 1;
                }));
        adminButtons.add(new Button(">", cx + contentW - smallBtnW, selectorBtnY, smallBtnW, 30,
                1.0f, 0.65f, 0.0f,
                () -> {
                    adminShapeIndex++;
                    if (adminShapeIndex >= adminShapeOptions.size())
                        adminShapeIndex = 0;
                }));
        cy += lineH;

        // Texture selector
        adminSelectedTexture = adminTextureOptions.isEmpty() ? "NONE (Color)"
                : adminTextureOptions.get(adminTextureIndex);
        String displayTex = adminSelectedTexture.length() > 16 ? adminSelectedTexture.substring(0, 16) + ".."
                : adminSelectedTexture;
        fontRenderer.drawText("Tex: " + displayTex, cx, cy, 0.8f, 0.8f, 0.8f);
        adminButtons.add(new Button("<", cx + contentW - smallBtnW * 2 - 10, cy - 5, smallBtnW, 30,
                1.0f, 0.65f, 0.0f,
                () -> {
                    adminTextureIndex--;
                    if (adminTextureIndex < 0)
                        adminTextureIndex = adminTextureOptions.size() - 1;
                }));
        adminButtons.add(new Button(">", cx + contentW - smallBtnW, cy - 5, smallBtnW, 30,
                1.0f, 0.65f, 0.0f,
                () -> {
                    adminTextureIndex++;
                    if (adminTextureIndex >= adminTextureOptions.size())
                        adminTextureIndex = 0;
                }));
        cy += lineH;

        // Spawn button
        adminButtons.add(new Button("SPAWN AT PLAYER", cx, cy, contentW, btnH,
                1.0f, 0.65f, 0.0f,
                () -> callback.onSpawnObject(adminSelectedShape, adminSelectedTexture)));
        cy += lineH + 10;

        // --- Section: Cheats ---
        fontRenderer.drawText("--- Cheats ---", cx, cy, 0.235f, 1.0f, 0.47f);
        cy += 35;

        String acText = adminAutoCollect ? "Auto Collect: ON" : "Auto Collect: OFF";
        adminButtons.add(new Button(acText, cx, cy, contentW, btnH,
                adminAutoCollect ? 0.235f : 0.5f, adminAutoCollect ? 1.0f : 0.5f, adminAutoCollect ? 0.47f : 0.5f,
                () -> {
                    adminAutoCollect = !adminAutoCollect;
                    callback.onToggleAutoCollect(adminAutoCollect);
                }));
        cy += lineH;

        if (adminDebugAvailable) {
            String dlText = adminDebugLines ? "Debug Lines: ON" : "Debug Lines: OFF";
            adminButtons.add(new Button(dlText, cx, cy, contentW, btnH,
                    0.0f, adminDebugLines ? 0.8f : 0.5f, adminDebugLines ? 1.0f : 0.5f,
                    () -> {
                        adminDebugLines = !adminDebugLines;
                        callback.onToggleDebugLines(adminDebugLines);
                    }));
            cy += lineH;
        }

        cy += 10;

        // --- Section: Player Teleport ---
        fontRenderer.drawText("--- Player Teleport ---", cx, cy, 0.39f, 0.58f, 0.93f);
        cy += 35;

        String posStr = String.format("Pos: (%.1f, %.1f, %.1f)", adminPlayerPos[0], adminPlayerPos[1],
                adminPlayerPos[2]);
        fontRenderer.drawText(posStr, cx, cy, 0.9f, 0.9f, 0.9f);
        cy += 30;

        float halfW = (contentW - 10) / 2.0f;
        adminButtons.add(new Button("COPY COORDS", cx, cy, halfW, btnH,
                0.39f, 0.58f, 0.93f,
                () -> callback.onCopyCoords()));
        adminButtons.add(new Button("TELEPORT", cx + halfW + 10, cy, halfW, btnH,
                0.39f, 0.58f, 0.93f,
                () -> callback.onTeleport(tpX, tpY, tpZ)));
        cy += lineH + 10;

        // --- Section: Ground Color ---
        fontRenderer.drawText("--- Ground Color ---", cx, cy, 0.235f, 1.0f, 0.47f);
        cy += 35;

        cy = renderSlider(cx, cy, contentW, "R", adminGroundR, 1.0f, 0.4f, 0.4f, (val) -> {
            adminGroundR = val;
            callback.onSetGroundColor(adminGroundR, adminGroundG, adminGroundB);
        });

        cy = renderSlider(cx, cy, contentW, "G", adminGroundG, 0.4f, 1.0f, 0.4f, (val) -> {
            adminGroundG = val;
            callback.onSetGroundColor(adminGroundR, adminGroundG, adminGroundB);
        });

        cy = renderSlider(cx, cy, contentW, "B", adminGroundB, 0.4f, 0.4f, 1.0f, (val) -> {
            adminGroundB = val;
            callback.onSetGroundColor(adminGroundR, adminGroundG, adminGroundB);
        });

        // Close button — sets the justClosed flag so GamePanel restores cursor
        cy += 10;
        adminButtons.add(new Button("CLOSE [F2]", cx, cy, contentW, btnH,
                0.6f, 0.6f, 0.6f,
                () -> {
                    adminPanelOpen = false;
                    adminPanelJustClosed = true;
                }));

        processButtons(adminButtons);
        consumeClick();
    }

    // ============================================================
    // SLIDER
    // ============================================================

    @FunctionalInterface
    private interface SliderCallback {
        void onValueChanged(float value);
    }

    private float renderSlider(float x, float y, float w, String label,
            float value, float colR, float colG, float colB,
            SliderCallback callback) {
        float sliderH = 20;
        float labelW = 30;
        float sliderX = x + labelW;
        float sliderW = w - labelW;

        fontRenderer.drawText(label + ":", x, y, colR, colG, colB);

        // Track background
        drawFilledRect(sliderX, y + 5, sliderW, sliderH, 0.2f, 0.2f, 0.2f, 1.0f);

        // Filled portion
        float filledW = sliderW * value;
        drawFilledRect(sliderX, y + 5, filledW, sliderH, colR, colG, colB, 0.7f);

        // Handle
        float handleX = sliderX + filledW - 5;
        float handleW = 10;
        drawFilledRect(handleX, y + 2, handleW, sliderH + 6, 1.0f, 1.0f, 1.0f, 0.9f);

        // Value text
        String valText = String.format("%.0f%%", value * 100);
        fontRenderer.drawText(valText, sliderX + sliderW + 5, y, 0.7f, 0.7f, 0.7f);

        // Interaction: drag on the slider track
        if (mousePressed) {
            float mx = (float) mouseX;
            float my = (float) mouseY;
            if (mx >= sliderX && mx <= sliderX + sliderW && my >= y && my <= y + sliderH + 10) {
                float newVal = (mx - sliderX) / sliderW;
                newVal = Math.max(0, Math.min(1, newVal));
                callback.onValueChanged(newVal);
            }
        }

        return y + 40;
    }

    // ============================================================
    // SHARED RENDERING HELPERS
    // ============================================================

    private void processButtons(List<Button> buttons) {
        float mx = (float) mouseX;
        float my = (float) mouseY;

        for (Button btn : buttons) {
            btn.hovered = btn.rect.contains(mx, my);

            if (btn.hovered && mouseJustClicked) {
                btn.action.run();
            }

            renderButton(btn);
        }
    }

    private void renderButton(Button btn) {
        float bgR, bgG, bgB, bgA;
        if (btn.hovered) {
            bgR = 0.2f;
            bgG = 0.2f;
            bgB = 0.22f;
            bgA = 0.95f;
        } else {
            bgR = 0.137f;
            bgG = 0.137f;
            bgB = 0.157f;
            bgA = 0.9f;
        }

        drawFilledRect(btn.rect.x, btn.rect.y, btn.rect.w, btn.rect.h, bgR, bgG, bgB, bgA);
        drawOutlineRect(btn.rect.x, btn.rect.y, btn.rect.w, btn.rect.h,
                btn.borderR, btn.borderG, btn.borderB);

        if (btn.hovered) {
            drawFilledRect(btn.rect.x, btn.rect.y, btn.rect.w, btn.rect.h,
                    btn.borderR, btn.borderG, btn.borderB, 0.1f);
        }

        float textX = btn.rect.x + (btn.rect.w / 2.0f) - (btn.label.length() * 8);
        float textY = btn.rect.y + (btn.rect.h / 2.0f) - 14;
        fontRenderer.drawText(btn.label, textX, textY,
                btn.borderR, btn.borderG, btn.borderB);
    }

    private void drawFilledRect(float x, float y, float w, float h,
            float r, float g, float b, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private void drawOutlineRect(float x, float y, float w, float h,
            float r, float g, float b) {
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
        glLineWidth(1.0f);
    }

    private void consumeClick() {
        mouseJustClicked = false;
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public void cleanup() {
        if (fontRenderer != null) {
            fontRenderer.cleanup();
        }
    }
}

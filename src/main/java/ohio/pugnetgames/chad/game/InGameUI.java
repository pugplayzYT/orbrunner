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

    // --- Update Log ---
    private UpdateLogManager updateLogManager;
    private int expandedLogIndex = -1; // -1 = list view, >=0 = showing detail
    private float logListScroll = 0; // scroll offset for the list
    private float logDetailScroll = 0; // scroll offset for the detail view
    private final List<Button> logButtons = new ArrayList<>();

    // Mouse state
    private double mouseX, mouseY;
    private boolean mousePressed = false;
    private boolean mouseJustClicked = false;
    private float mouseScrollDelta = 0; // accumulated scroll delta

    // Screen dimensions
    private int screenW, screenH;

    // ============================================================
    // INIT
    // ============================================================

    public void init() {
        fontRenderer = new FontRenderer();
        fontRenderer.init("inter_extracted/extras/ttf/Inter-Regular.ttf");

        // Load update logs
        updateLogManager = new UpdateLogManager();
        updateLogManager.loadAll();
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

    /** Called from GamePanel's GLFW scroll callback. */
    public void onMouseScroll(double yOffset) {
        mouseScrollDelta += (float) yOffset;
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

        // --- Calculate total content height so we can center the whole block ---
        // Title line: ~48px, subtitle: ~30px, best score: ~30px, gap: 25px, 3 buttons +
        // gaps
        float btnW = 420;
        float btnH = 50;
        float btnGap = 15;
        float titleBlockH = 48 + 30 + 30 + 25; // title + subtitle + best + gap before buttons
        float buttonsBlockH = btnH * 3 + btnGap * 2;
        float totalH = titleBlockH + buttonsBlockH;
        float startY = (height - totalH) / 2.0f - 30; // center vertically, nudge slightly up

        // Title
        String title = "MAZE ESCAPE 3D";
        float titleW = fontRenderer.getTextWidth(title);
        float titleX = (width - titleW) / 2.0f;
        float titleY = startY;
        fontRenderer.drawText(title, titleX - 2, titleY - 2, 0.0f, 0.6f, 0.2f); // glow
        fontRenderer.drawText(title, titleX, titleY, 0.235f, 1.0f, 0.47f);

        // Subtitle
        String subtitle = "LWJGL OPENGL EDITION  " + updateLogManager.getLatestVersion();
        float subW = fontRenderer.getTextWidth(subtitle);
        float subX = (width - subW) / 2.0f;
        float subY = titleY + 50;
        fontRenderer.drawText(subtitle, subX, subY, 0.6f, 0.6f, 0.6f);

        // Best score
        long bestScore = ScoreManager.loadBestScore();
        String bestText = "BEST WINS: " + bestScore;
        float bestW = fontRenderer.getTextWidth(bestText);
        float bestX = (width - bestW) / 2.0f;
        float bestY = subY + 35;
        fontRenderer.drawText(bestText, bestX, bestY, 0.8f, 0.8f, 0.8f);

        // Buttons — right below text block
        menuButtons.clear();
        float btnX = (width - btnW) / 2.0f;
        float btnStartY = bestY + 50;

        menuButtons.add(new Button("EASY (3 Keys, +1 Win)", btnX, btnStartY, btnW, btnH,
                0.235f, 1.0f, 0.47f,
                () -> selectedDifficulty = Difficulty.EASY));

        menuButtons.add(new Button("HARD (10 Keys, +5 Wins)", btnX, btnStartY + btnH + btnGap, btnW, btnH,
                1.0f, 0.4f, 0.2f,
                () -> selectedDifficulty = Difficulty.HARD));

        menuButtons.add(new Button("QUIT", btnX, btnStartY + (btnH + btnGap) * 2, btnW, btnH,
                0.6f, 0.6f, 0.6f,
                () -> quitRequested = true));

        processButtons(menuButtons);

        // Credits
        String credits1 = "A PugNet Games Production";
        float c1W = fontRenderer.getTextWidth(credits1);
        float c1X = (width - c1W) / 2.0f;
        fontRenderer.drawText(credits1, c1X, height - 110, 0.235f, 1.0f, 0.47f);

        String credits2 = "Powered by the Chad Engine";
        float c2W = fontRenderer.getTextWidth(credits2);
        float c2X = (width - c2W) / 2.0f;
        fontRenderer.drawText(credits2, c2X, height - 80, 0.4f, 0.35f, 0.5f);

        // Controls footer
        String footer = "WASD to move  |  Mouse to look  |  ESC for pause menu";
        float footW = fontRenderer.getTextWidth(footer);
        float footX = (width - footW) / 2.0f;
        fontRenderer.drawText(footer, footX, height - 45, 0.35f, 0.35f, 0.35f);

        // --- Update Log Panel (right side) ---
        renderUpdateLogPanel(width, height);

        consumeClick();
        mouseScrollDelta = 0; // consume scroll
    }

    // ============================================================
    // UPDATE LOG PANEL (on main menu)
    // ============================================================

    private void renderUpdateLogPanel(int width, int height) {
        if (expandedLogIndex >= 0) {
            renderUpdateLogDetail(width, height);
            return;
        }

        // Panel on the right side — wide enough for titles
        float panelW = 440;
        float panelH = height - 80;
        float panelX = width - panelW - 15;
        float panelY = 20;

        // Background
        drawFilledRect(panelX, panelY, panelW, panelH, 0.08f, 0.085f, 0.11f, 0.92f);
        drawOutlineRect(panelX, panelY, panelW, panelH, 0.235f, 1.0f, 0.47f);

        // Title
        String logTitle = "UPDATE LOG";
        float ltW = fontRenderer.getTextWidth(logTitle);
        float ltX = panelX + (panelW - ltW) / 2.0f;
        fontRenderer.drawText(logTitle, ltX, panelY + 10, 0.235f, 1.0f, 0.47f);

        // Separator line
        drawFilledRect(panelX + 10, panelY + 52, panelW - 20, 1, 0.3f, 0.3f, 0.35f, 0.6f);

        float contentX = panelX + 10;
        float contentY = panelY + 60;
        float contentW = panelW - 20;
        float contentH = panelH - 70;

        // Handle scrolling (only when mouse is within panel)
        float mx = (float) mouseX;
        float my = (float) mouseY;
        if (mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH) {
            logListScroll -= mouseScrollDelta * 35;
        }

        // Calculate total content height for scroll limits
        List<UpdateLogManager.UpdateEntry> entries = updateLogManager.getEntries();
        float entryH = 55;
        float totalListH = entries.size() * entryH;
        float maxScroll = Math.max(0, totalListH - contentH);
        logListScroll = Math.max(0, Math.min(maxScroll, logListScroll));

        // Scissor test for clipping
        glEnable(GL_SCISSOR_TEST);
        // OpenGL scissor uses bottom-left origin, our UI uses top-left
        int scissorX = (int) panelX;
        int scissorY = screenH - (int) (contentY + contentH);
        int scissorW = (int) panelW;
        int scissorH = (int) contentH;
        glScissor(scissorX, scissorY, scissorW, scissorH);

        logButtons.clear();
        float drawY = contentY - logListScroll;

        for (int i = 0; i < entries.size(); i++) {
            UpdateLogManager.UpdateEntry entry = entries.get(i);

            // Only create clickable buttons for visible entries
            if (drawY + entryH > contentY && drawY < contentY + contentH) {
                // Entry background (subtle alternating)
                float bgAlpha = (i % 2 == 0) ? 0.15f : 0.08f;
                drawFilledRect(contentX, drawY, contentW, entryH - 5, 0.2f, 0.2f, 0.25f, bgAlpha);

                // Green dot marker (vertically centered)
                drawFilledRect(contentX + 8, drawY + 18, 8, 8, 0.235f, 1.0f, 0.47f, 1.0f);

                // Title text
                String entryTitle = entry.title;
                float maxTextW = contentW - 35;
                if (fontRenderer.getTextWidth(entryTitle) > maxTextW) {
                    while (entryTitle.length() > 5 && fontRenderer.getTextWidth(entryTitle + "..") > maxTextW) {
                        entryTitle = entryTitle.substring(0, entryTitle.length() - 1);
                    }
                    entryTitle += "..";
                }
                fontRenderer.drawText(entryTitle, contentX + 24, drawY + 4, 0.85f, 0.85f, 0.9f);

                // Hover highlight + click
                boolean hovered = mx >= contentX && mx <= contentX + contentW
                        && my >= drawY && my <= drawY + entryH - 5;
                if (hovered) {
                    drawFilledRect(contentX, drawY, contentW, entryH - 5, 0.235f, 1.0f, 0.47f, 0.1f);
                    if (mouseJustClicked) {
                        expandedLogIndex = i;
                        logDetailScroll = 0;
                    }
                }
            }

            drawY += entryH;
        }

        glDisable(GL_SCISSOR_TEST);

        // Scroll indicator if content overflows
        if (totalListH > contentH) {
            float scrollBarH = Math.max(20, contentH * (contentH / totalListH));
            float scrollBarY = contentY + (logListScroll / maxScroll) * (contentH - scrollBarH);
            float scrollBarX = panelX + panelW - 8;
            drawFilledRect(scrollBarX, scrollBarY, 4, scrollBarH, 0.5f, 0.5f, 0.5f, 0.5f);
        }

        if (entries.isEmpty()) {
            fontRenderer.drawText("No update logs found.", contentX + 10, contentY + 10, 0.5f, 0.5f, 0.5f);
        }
    }

    /**
     * Renders the expanded update log detail overlay.
     */
    private void renderUpdateLogDetail(int width, int height) {
        List<UpdateLogManager.UpdateEntry> entries = updateLogManager.getEntries();
        if (expandedLogIndex < 0 || expandedLogIndex >= entries.size()) {
            expandedLogIndex = -1;
            return;
        }

        UpdateLogManager.UpdateEntry entry = entries.get(expandedLogIndex);

        // Larger centered panel
        float panelW = Math.min(700, width - 60);
        float panelH = height - 60;
        float panelX = (width - panelW) / 2.0f;
        float panelY = 30;

        // Darken background
        drawFilledRect(0, 0, width, height, 0.0f, 0.0f, 0.0f, 0.6f);

        // Panel background
        drawFilledRect(panelX, panelY, panelW, panelH, 0.094f, 0.102f, 0.125f, 0.97f);
        drawOutlineRect(panelX, panelY, panelW, panelH, 0.235f, 1.0f, 0.47f);

        // Title bar
        float titleBarH = 55;
        drawFilledRect(panelX, panelY, panelW, titleBarH, 0.12f, 0.13f, 0.17f, 1.0f);

        // Title text
        fontRenderer.drawText(entry.title, panelX + 15, panelY + 6, 0.235f, 1.0f, 0.47f);

        // X close button (top right)
        float closeBtnSize = 40;
        float closeBtnX = panelX + panelW - closeBtnSize - 8;
        float closeBtnY = panelY + 8;
        logButtons.clear();
        logButtons.add(new Button("X", closeBtnX, closeBtnY, closeBtnSize, closeBtnSize,
                1.0f, 0.3f, 0.3f,
                () -> {
                    expandedLogIndex = -1;
                    logDetailScroll = 0;
                }));

        // Content area
        float contentX = panelX + 20;
        float contentY = panelY + titleBarH + 10;
        float contentW = panelW - 40;
        float contentH = panelH - titleBarH - 20;

        // Handle scrolling
        float mx = (float) mouseX;
        float my = (float) mouseY;
        if (mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH) {
            logDetailScroll -= mouseScrollDelta * 40;
        }

        // Calculate total height with word wrapping
        float subLineH = 40; // height per wrapped sub-line
        float totalH = 10;
        for (UpdateLogManager.MarkdownLine ml : entry.lines) {
            if (ml.type == UpdateLogManager.MarkdownLine.Type.BLANK) {
                totalH += 18;
            } else {
                float indent = getMarkdownIndent(ml);
                float availW = contentW - indent;
                List<String> wrapped = wrapText(ml.text, availW);
                int lineCount = Math.max(1, wrapped.size());
                // First line gets extra spacing for headers
                totalH += getMarkdownFirstLineExtra(ml) + lineCount * subLineH;
            }
        }
        float maxScroll = Math.max(0, totalH - contentH);
        logDetailScroll = Math.max(0, Math.min(maxScroll, logDetailScroll));

        // Scissor for content clipping
        glEnable(GL_SCISSOR_TEST);
        glScissor((int) contentX, screenH - (int) (contentY + contentH),
                (int) contentW, (int) contentH);

        float drawY = contentY + 5 - logDetailScroll;

        for (UpdateLogManager.MarkdownLine ml : entry.lines) {
            if (ml.type == UpdateLogManager.MarkdownLine.Type.BLANK) {
                drawY += 18;
                continue;
            }

            float indent = getMarkdownIndent(ml);
            float availW = contentW - indent;
            List<String> wrapped = wrapText(ml.text, availW);
            float extra = getMarkdownFirstLineExtra(ml);
            drawY += extra;

            for (int wi = 0; wi < wrapped.size(); wi++) {
                if (drawY + subLineH > contentY - 50 && drawY < contentY + contentH + 50) {
                    renderMarkdownSubLine(ml, wrapped.get(wi), contentX, drawY, contentW, wi == 0);
                }
                drawY += subLineH;
            }
        }

        glDisable(GL_SCISSOR_TEST);

        // Scroll bar
        if (totalH > contentH) {
            float scrollBarH = Math.max(20, contentH * (contentH / totalH));
            float scrollBarY = contentY + (logDetailScroll / maxScroll) * (contentH - scrollBarH);
            float scrollBarX = panelX + panelW - 10;
            drawFilledRect(scrollBarX, scrollBarY, 4, scrollBarH, 0.5f, 0.5f, 0.5f, 0.5f);
        }

        // Process X button
        processButtons(logButtons);
    }

    /** Left indent for each markdown type (bullets and text are indented). */
    private float getMarkdownIndent(UpdateLogManager.MarkdownLine ml) {
        switch (ml.type) {
            case H1:
                return 0;
            case H2:
                return 0;
            case H3:
                return 5;
            case BULLET:
                return 28; // past the dot
            case TEXT:
                return 5;
            default:
                return 0;
        }
    }

    /** Extra spacing added BEFORE the first sub-line of a header. */
    private float getMarkdownFirstLineExtra(UpdateLogManager.MarkdownLine ml) {
        switch (ml.type) {
            case H1:
                return 8;
            case H2:
                return 5;
            case H3:
                return 4;
            default:
                return 0;
        }
    }

    /**
     * Renders a single sub-line of a wrapped markdown line.
     * 
     * @param isFirstSubLine true if this is the first sub-line (gets the bullet
     *                       dot, underline, etc.)
     */
    private void renderMarkdownSubLine(UpdateLogManager.MarkdownLine ml, String text,
            float x, float y, float maxW, boolean isFirstSubLine) {
        switch (ml.type) {
            case H1:
                fontRenderer.drawText(text, x, y, 0.235f, 1.0f, 0.47f);
                break;
            case H2:
                fontRenderer.drawText(text, x, y, 0.6f, 0.75f, 1.0f);
                break;
            case H3:
                fontRenderer.drawText(text, x + 5, y, 1.0f, 0.75f, 0.3f);
                break;
            case BULLET:
                if (isFirstSubLine) {
                    drawFilledCircle(x + 15, y + 6, 3, 0.6f, 0.8f, 0.6f, 1.0f);
                }
                fontRenderer.drawText(text, x + 28, y, 0.8f, 0.8f, 0.8f);
                break;
            case TEXT:
                fontRenderer.drawText(text, x + 5, y, 0.75f, 0.75f, 0.75f);
                break;
            case BLANK:
                break;
        }
    }

    /**
     * Splits text into multiple lines that fit within maxWidth pixels.
     * Splits at word boundaries (spaces). If a single word is too wide,
     * it gets its own line and will be clipped by the scissor test.
     */
    private List<String> wrapText(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() == 0) {
                // First word on the line — always add it
                current.append(word);
            } else {
                // Check if adding this word exceeds width
                String test = current.toString() + " " + word;
                if (fontRenderer.getTextWidth(test) <= maxWidth) {
                    current.append(" ").append(word);
                } else {
                    // Flush current line, start new one
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
        }

        // Don't forget the last line
        if (current.length() > 0) {
            lines.add(current.toString());
        }

        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
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
        float panelW = 480;
        float panelH = 560;
        float panelX = (width - panelW) / 2.0f;
        float panelY = (height - panelH) / 2.0f;
        drawFilledRect(panelX, panelY, panelW, panelH, 0.094f, 0.102f, 0.125f, 0.96f);
        drawOutlineRect(panelX, panelY, panelW, panelH, 0.235f, 1.0f, 0.47f);

        float cx = panelX + 25;
        float cy = panelY + 20;
        float contentW = panelW - 50;
        float btnH = 42;

        // Title (centered in panel)
        String title = "PAUSED";
        float titleW = fontRenderer.getTextWidth(title);
        float titleX = panelX + (panelW - titleW) / 2.0f;
        fontRenderer.drawText(title, titleX, cy, 0.235f, 1.0f, 0.47f);
        cy += 50;

        // Resume button
        pauseButtons.clear();
        pauseButtons.add(new Button("RESUME", cx, cy, contentW, btnH,
                0.235f, 1.0f, 0.47f,
                () -> pauseResumeRequested = true));
        cy += btnH + 20;

        // --- Settings Section ---
        fontRenderer.drawText("--- Settings ---", cx, cy, 0.8f, 0.6f, 1.0f);
        cy += 35;

        // Sensitivity slider (0.01 - 0.5)
        float sensNorm = (sensitivity - 0.01f) / (0.5f - 0.01f);
        cy = renderSliderWithValue(cx, cy, contentW, "Sens", sensNorm,
                String.format("%.2f", sensitivity), 0.8f, 0.6f, 1.0f, (val) -> {
                    sensitivity = 0.01f + val * (0.5f - 0.01f);
                });

        // FOV slider (30 - 120)
        float fovNorm = (fieldOfView - 30.0f) / (120.0f - 30.0f);
        cy = renderSliderWithValue(cx, cy, contentW, "FOV", fovNorm,
                String.format("%.0f", fieldOfView), 1.0f, 0.8f, 0.3f, (val) -> {
                    fieldOfView = 30.0f + val * (120.0f - 30.0f);
                });

        // Fog density slider (0.0 - 0.2)
        float fogNorm = fogDensity / 0.2f;
        cy = renderSliderWithValue(cx, cy, contentW, "Fog", fogNorm,
                String.format("%.3f", fogDensity), 0.5f, 0.7f, 1.0f, (val) -> {
                    fogDensity = val * 0.2f;
                });

        // Volume slider (0.0 - 1.0)
        cy = renderSliderWithValue(cx, cy, contentW, "Vol", masterVolume,
                String.format("%.0f%%", masterVolume * 100), 0.3f, 1.0f, 0.5f, (val) -> {
                    masterVolume = val;
                });

        cy += 5;

        // --- Fun Toggles ---
        fontRenderer.drawText("--- Extras ---", cx, cy, 1.0f, 0.65f, 0.0f);
        cy += 35;

        // Invert Y toggle
        String invText = invertY ? "Invert Y: ON" : "Invert Y: OFF";
        pauseButtons.add(new Button(invText, cx, cy, contentW, 38,
                invertY ? 1.0f : 0.5f, invertY ? 0.65f : 0.5f, invertY ? 0.0f : 0.5f,
                () -> invertY = !invertY));
        cy += 50;

        // Quit to Menu button
        pauseButtons.add(new Button("QUIT TO MENU", cx, cy, contentW, btnH,
                1.0f, 0.3f, 0.3f,
                () -> pauseQuitToMenuRequested = true));

        processButtons(pauseButtons);

        // Hint at bottom of panel
        String hint = "Press ESC to resume";
        float hintW = fontRenderer.getTextWidth(hint);
        float hintX = panelX + (panelW - hintW) / 2.0f;
        fontRenderer.drawText(hint, hintX, panelY + panelH - 30, 0.4f, 0.4f, 0.4f);

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
        float goTitleW = fontRenderer.getTextWidth(goTitle);
        float goTitleX = panelX + (panelW - goTitleW) / 2.0f;
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

        void onSpawnObject(String shape, String textureName);

        void onTeleport(float x, float y, float z);

        void onCopyCoords();
    }

    public void syncAdminState(boolean autoCollect, boolean debugLines, boolean debugAvailable,
            float[] playerPos,
            Map<String, Integer> textures) {
        this.adminAutoCollect = autoCollect;
        this.adminDebugLines = debugLines;
        this.adminDebugAvailable = debugAvailable;
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

        float panelW = 460;
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
        float labelW = 40; // width for single-char labels like R, G, B
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

    /**
     * Slider variant for the pause menu — shows label, slider bar, and value text.
     * Label column is wider to fit labels like "Sens", "FOV", "Fog", "Vol".
     */
    private float renderSliderWithValue(float x, float y, float w, String label,
            float value, String valueText,
            float colR, float colG, float colB,
            SliderCallback callback) {
        float sliderH = 20;
        float labelW = 70; // wider for multi-char labels
        float valueDisplayW = 70; // reserve space for value text on right
        float sliderX = x + labelW;
        float sliderW = w - labelW - valueDisplayW;

        // Label
        fontRenderer.drawText(label + ":", x, y, colR, colG, colB);

        // Track background
        drawFilledRect(sliderX, y + 5, sliderW, sliderH, 0.2f, 0.2f, 0.2f, 1.0f);

        // Filled portion
        float filledW = sliderW * Math.max(0, Math.min(1, value));
        drawFilledRect(sliderX, y + 5, filledW, sliderH, colR, colG, colB, 0.7f);

        // Handle
        float handleX = sliderX + filledW - 5;
        drawFilledRect(handleX, y + 2, 10, sliderH + 6, 1.0f, 1.0f, 1.0f, 0.9f);

        // Value text (right-aligned after slider)
        fontRenderer.drawText(valueText, sliderX + sliderW + 8, y, 0.7f, 0.7f, 0.7f);

        // Interaction
        if (mousePressed) {
            float mx = (float) mouseX;
            float my = (float) mouseY;
            if (mx >= sliderX && mx <= sliderX + sliderW && my >= y && my <= y + sliderH + 10) {
                float newVal = (mx - sliderX) / sliderW;
                newVal = Math.max(0, Math.min(1, newVal));
                callback.onValueChanged(newVal);
            }
        }

        return y + 38;
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

        float textX = btn.rect.x + (btn.rect.w - fontRenderer.getTextWidth(btn.label)) / 2.0f;
        float textY = btn.rect.y + (btn.rect.h / 2.0f) - 9;
        fontRenderer.drawText(btn.label, textX, textY,
                btn.borderR, btn.borderG, btn.borderB);
    }

    private void drawFilledCircle(float cx, float cy, float radius,
            float r, float g, float b, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(r, g, b, a);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        int segments = 16;
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            glVertex2f(cx + (float) (Math.cos(angle) * radius), cy + (float) (Math.sin(angle) * radius));
        }
        glEnd();
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

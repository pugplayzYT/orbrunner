package ohio.pugnetgames.chad.game;

import ohio.pugnetgames.chad.core.BuildManager;
import ohio.pugnetgames.chad.core.Difficulty;
import ohio.pugnetgames.chad.core.RunData;
import ohio.pugnetgames.chad.core.RunManager;
import ohio.pugnetgames.chad.core.RunState;
import ohio.pugnetgames.chad.core.ScoreManager;
import ohio.pugnetgames.chad.core.SoundManager;
import ohio.pugnetgames.chad.game.GameObject.ShapeType;
import ohio.pugnetgames.chad.game.Room.RoomType;

import ohio.pugnetgames.chad.game.PathNode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GamePanel handles the main game loop, window, and orchestrates all game
 * systems.
 *
 * REWRITTEN: No longer uses Swing. All UI (main menu, admin panel, game over)
 * is rendered in-game via InGameUI using OpenGL.
 *
 * Game States: MAIN_MENU -> PLAYING -> GAME_OVER -> MAIN_MENU
 */
public class GamePanel extends Thread {

    // ============================================================
    // GAME STATE MACHINE
    // ============================================================
    public enum GameState {
        MAIN_MENU,
        PLAYING,
        PAUSED,
        GAME_OVER
    }

    private GameState gameState = GameState.MAIN_MENU;

    private long window;
    private volatile boolean isRunning = true; // controls the main loop
    private long currentScore;
    private long bestScoreCache;

    // --- GAME FIELDS ---
    private int TOTAL_KEYS;
    private int keysCollected = 0;
    private GameObject escapeDoor;
    private GameObject winTrigger;
    private List<Key> adminKeys = new ArrayList<>();
    private SoundManager soundManager;

    // --- HORROR FIELDS ---
    private float horrorLevel = 0.0f;
    private final float HORROR_RATE_STANDARD = 0.005f;
    private final float HORROR_RATE_COURTYARD = 0.001f;
    private final String CRACKLE_SOUND_FILE = "crackle.mp3";

    // --- OBJECTIVE / HUD FIELDS ---
    private String currentObjectiveText = "";
    private String popupMessage = "";
    private float popupMessageTimer = 0.0f;
    private final float POPUP_MESSAGE_DURATION = 4.0f;
    private final float FRAME_TIME_ESTIMATE = 0.0166f;
    private boolean allKeysCollectedMessageTriggered = false;
    private String hotColdText = "";

    // --- RUNS SYSTEM ---
    private RunManager runManager;
    private RunData activeRun = null;
    private long runStartTimeMs = 0;
    private long runElapsedMs = 0; // accumulated elapsed from previous sessions

    // --- GAME SYSTEMS ---
    private World world;
    private Player player;
    private InputHandler inputHandler;
    private KeyManager keyManager;
    private HudRenderer hudRenderer;
    private DebugRenderer debugRenderer;
    private WorldLoader worldLoader;
    private InGameUI inGameUI;

    // --- DIY A* PATHFINDING AI ---
    private PathfindingManager pathfinder;
    private List<PathNode> aiPath;
    private int aiPathIndex;

    private enum AiState {
        IDLE, FINDING_PATH, FOLLOWING_PATH, PATH_FAILED
    }

    private AiState aiState = AiState.IDLE;
    private float aiFailedPathTimer = 0.0f;
    private final float AI_RETRY_COOLDOWN = 3.0f;

    // --- Textures ---
    private int orbTextureID;
    private int wallTextureID;
    private int woodTextureID;
    private int sheetsTextureID;
    private Map<String, Integer> loadedTextures = new HashMap<>();

    // --- Game State ---
    private float FIELD_OF_VIEW = 60.0f; // mutable — adjusted from pause menu
    private final float NEAR_PLANE = 0.1f;
    private final float FAR_PLANE = 100.0f;

    private boolean freeCamFeatureAvailable = false;
    private volatile boolean isFreeCamActive = false;
    private boolean adminPanelFeatureAvailable = false;
    private volatile boolean isAutoCollectActive = false;
    private volatile float groundR = 0.1f, groundG = 0.5f, groundB = 0.2f;
    private boolean debugLinesFeatureAvailable = false;
    private volatile boolean isDebugLinesActive = false;
    private volatile boolean isMapActive = false;

    // Current difficulty (set when game starts from menu)
    private Difficulty difficulty = Difficulty.EASY;

    // Mouse cursor visibility state tracking
    private boolean cursorVisible = true;

    // Game over state
    private String gameOverMessage = "";
    private boolean gameOverIsWin = false;

    public GamePanel() {
        // No-arg constructor — everything is managed internally
    }

    @Override
    public void run() {
        init();
        mainLoop();
        dispose();
    }

    // ============================================================
    // INITIALIZATION
    // ============================================================

    private void init() {
        this.freeCamFeatureAvailable = BuildManager.getBoolean("feature.freecam.enabled");
        this.adminPanelFeatureAvailable = BuildManager.getBoolean("feature.adminpanel.enabled");
        this.debugLinesFeatureAvailable = BuildManager.getBoolean("feature.debuglines.enabled");

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            System.err.println("FATAL: Unable to initialize GLFW.");
            return;
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        long primaryMonitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidmode = glfwGetVideoMode(primaryMonitor);
        int width = vidmode.width();
        int height = vidmode.height();
        window = glfwCreateWindow(width, height, "Maze Escape 3D - OpenGL", primaryMonitor, NULL);
        if (window == NULL) {
            System.err.println("FATAL: Failed to create window, trying fallback.");
            width = 800;
            height = 600;
            window = glfwCreateWindow(width, height, "Maze Escape 3D - OpenGL", NULL, NULL);
            if (window == NULL) {
                System.err.println("FATAL: Failed to create window in fallback mode.");
                return;
            }
        }

        // Key callback — we handle it ourselves, InputHandler gets created later
        glfwSetKeyCallback(window, this::globalKeyCallback);

        // Mouse callbacks for UI
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (inGameUI != null) {
                inGameUI.onMouseMove(xpos, ypos);
            }
            // Forward to input handler during gameplay
            if (inputHandler != null && gameState == GameState.PLAYING && !cursorVisible) {
                inputHandler.cursorPosCallback.invoke(win, xpos, ypos);
            }
        });
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (inGameUI != null) {
                inGameUI.onMouseButton(button, action);
            }
        });
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (inGameUI != null) {
                inGameUI.onMouseScroll(yoffset);
            }
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        // Load all textures
        List<String> textureNames = TextureLoader.getAllTextureFilenames();
        for (String name : textureNames) {
            int id = TextureLoader.loadTexture(name);
            if (id != 0) {
                loadedTextures.put(name, id);
            }
        }

        orbTextureID = loadedTextures.getOrDefault("orb_texture.png", 0);
        wallTextureID = loadedTextures.getOrDefault("tunnel_texture.png", 0);
        woodTextureID = loadedTextures.getOrDefault("wood_texture.png", 0);
        sheetsTextureID = loadedTextures.getOrDefault("sheets_texture.png", 0);

        // Initialize game systems that persist across rounds
        runManager  = new RunManager();
        worldLoader = new WorldLoader();
        keyManager  = new KeyManager();
        hudRenderer = new HudRenderer();
        debugRenderer = new DebugRenderer();
        soundManager = new SoundManager();
        hudRenderer.init();

        // Initialize in-game UI
        inGameUI = new InGameUI();
        inGameUI.init();

        // Watch for settings changes from the launcher
        inGameUI.getSettingsManager().watchForExternalChanges(s -> {
            inGameUI.loadFromSettingsFile();
        });

        // Forward typed characters to InGameUI for text-input fields (Create Run screen)
        glfwSetCharCallback(window, (win, codepoint) -> {
            if (inGameUI != null) inGameUI.onChar((int) codepoint);
        });

        // Populate the runs list on startup (normally done by transitionToMainMenu,
        // but the game launches directly into MAIN_MENU so it must be seeded here)
        inGameUI.refreshRunsList(runManager.loadAllRuns());

        // OpenGL state
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_LIGHTING);
        glDisable(GL_LIGHT0);
        glEnable(GL_NORMALIZE);
        glShadeModel(GL_SMOOTH);
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);

        float[] globalAmbient = { 0.8f, 0.8f, 0.8f, 1.0f };
        glLightModelfv(GL_LIGHT_MODEL_AMBIENT, globalAmbient);

        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_EXP2);
        glFogfv(GL_FOG_COLOR, new float[] { 0.0f, 0.0f, 0.0f, 1.0f });
        glFogf(GL_FOG_DENSITY, 0.07f);

        // Start in main menu state — show cursor
        setCursorVisible(true);
        gameState = GameState.MAIN_MENU;
    }

    // ============================================================
    // MAIN LOOP — dispatches to the current state
    // ============================================================

    private void mainLoop() {
        while (!glfwWindowShouldClose(window) && isRunning) {
            glfwPollEvents();

            switch (gameState) {
                case MAIN_MENU:
                    updateMainMenu();
                    renderMainMenu();
                    break;
                case PLAYING:
                    updateGame();
                    render();
                    break;
                case PAUSED:
                    updatePaused();
                    renderPaused();
                    break;
                case GAME_OVER:
                    updateGameOver();
                    renderGameOver();
                    break;
            }

            glfwSwapBuffers(window);
        }
    }

    // ============================================================
    // CURSOR MANAGEMENT
    // ============================================================

    private void setCursorVisible(boolean visible) {
        if (visible) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        } else {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }
        cursorVisible = visible;
    }

    // ============================================================
    // GLOBAL KEY CALLBACK
    // ============================================================

    private void globalKeyCallback(long window, int key, int scode, int action, int mods) {
        // Forward to InputHandler for gameplay keys
        if (inputHandler != null && gameState == GameState.PLAYING) {
            inputHandler.keyCallback.invoke(window, key, scode, action, mods);
        }

        // Forward to InGameUI for text-input handling (backspace, enter)
        if (inGameUI != null) {
            inGameUI.onKey(key, action);
        }

        if (action == GLFW_RELEASE) {
            switch (gameState) {
                case MAIN_MENU:
                    if (key == GLFW_KEY_ESCAPE) {
                        // Let InGameUI navigate back through sub-screens first
                        if (inGameUI == null || !inGameUI.consumeMenuEscape()) {
                            isRunning = false;
                            glfwSetWindowShouldClose(window, true);
                        }
                    }
                    break;

                case PLAYING:
                    if (key == GLFW_KEY_ESCAPE) {
                        // Open pause menu instead of quitting
                        transitionToPaused();
                    } else if (key == GLFW_KEY_M) {
                        isMapActive = !isMapActive;
                        if (isMapActive) {
                            isFreeCamActive = false;
                            isAutoCollectActive = false;
                            if (inputHandler != null) {
                                inputHandler.wPressed = false;
                                inputHandler.sPressed = false;
                                inputHandler.aPressed = false;
                                inputHandler.dPressed = false;
                            }
                        }
                    } else if (key == GLFW_KEY_P) {
                        if (freeCamFeatureAvailable) {
                            isFreeCamActive = !isFreeCamActive;
                            if (isFreeCamActive) {
                                isAutoCollectActive = false;
                                aiState = AiState.IDLE;
                                if (inputHandler != null)
                                    inputHandler.wPressed = false;
                            }
                        }
                    } else if (key == GLFW_KEY_F2) {
                        if (adminPanelFeatureAvailable && inGameUI != null) {
                            inGameUI.toggleAdminPanel();
                            if (inGameUI.isAdminPanelOpen()) {
                                setCursorVisible(true);
                                inGameUI.syncAdminState(isAutoCollectActive, isDebugLinesActive,
                                        debugLinesFeatureAvailable,
                                        getPlayerPosition(), loadedTextures);
                            } else {
                                setCursorVisible(false);
                                if (inputHandler != null)
                                    inputHandler.resetMouse();
                            }
                        }
                    }
                    break;

                case PAUSED:
                    if (key == GLFW_KEY_ESCAPE) {
                        // ESC again = resume
                        transitionFromPaused();
                    }
                    break;

                case GAME_OVER:
                    if (key == GLFW_KEY_ESCAPE) {
                        transitionToMainMenu();
                    }
                    break;
            }
        }
    }

    // ============================================================
    // STATE TRANSITIONS
    // ============================================================

    private void transitionToMainMenu() {
        // Auto-save the active run before returning to the menu
        if (activeRun != null && !activeRun.isCompleted()) {
            saveCurrentRunState();
        }
        activeRun = null;

        gameState = GameState.MAIN_MENU;
        setCursorVisible(true);
        if (inGameUI != null) {
            inGameUI.closeAdminPanel();
            inGameUI.refreshRunsList(runManager.loadAllRuns());
        }
        if (soundManager != null)
            soundManager.stopAmbiance();
        bestScoreCache = ScoreManager.loadBestScore();
        // Stop movement
        if (inputHandler != null) {
            inputHandler.wPressed = false;
            inputHandler.sPressed = false;
            inputHandler.aPressed = false;
            inputHandler.dPressed = false;
        }
    }

    private void transitionToRun(RunData run) {
        this.activeRun  = run;
        this.difficulty = run.difficulty;
        this.TOTAL_KEYS = (run.difficulty == Difficulty.HARD) ? 10 : 3;

        setCursorVisible(false);
        if (inGameUI != null)
            inGameUI.closeAdminPanel();

        RunState saved = runManager.loadRunState(run);
        if (saved != null && saved.keysCollected.length == TOTAL_KEYS) {
            // Continue a saved run: regenerate the same world with the stored seed
            startGame(saved.worldSeed);
            player.setPosX(saved.playerX);
            player.setPosY(saved.playerY);
            player.setPosZ(saved.playerZ);
            player.setYaw(saved.yaw);
            player.setPitch(saved.pitch);
            // Restore key collection state
            List<Key> keys = keyManager.getKeys();
            int restoredCount = 0;
            for (int i = 0; i < keys.size() && i < saved.keysCollected.length; i++) {
                if (saved.keysCollected[i]) {
                    keys.get(i).collected = true;
                    restoredCount++;
                }
            }
            keyManager.setKeysCollected(restoredCount);
            keysCollected = restoredCount;
            if (keysCollected == TOTAL_KEYS) {
                // All keys already collected — open the door
                openEscapeDoor();
            }
            runElapsedMs = run.elapsedMs;
        } else {
            // Brand-new run
            startGame();
            runElapsedMs = 0;
            // Save initial state so the run folder has a valid state.dat
            saveCurrentRunState();
        }

        runStartTimeMs = System.currentTimeMillis();
        updateObjectiveText();
        gameState = GameState.PLAYING;
    }

    private void transitionToPaused() {
        gameState = GameState.PAUSED;
        setCursorVisible(true);
        // Freeze player movement
        if (inputHandler != null) {
            inputHandler.wPressed = false;
            inputHandler.sPressed = false;
            inputHandler.aPressed = false;
            inputHandler.dPressed = false;
        }
    }

    private void transitionFromPaused() {
        gameState = GameState.PLAYING;
        setCursorVisible(false);
        if (inputHandler != null)
            inputHandler.resetMouse();
        // Apply settings from pause menu
        applyPauseMenuSettings();
    }

    private void applyPauseMenuSettings() {
        if (inGameUI == null)
            return;
        // FOV
        FIELD_OF_VIEW = inGameUI.getFieldOfView();
        // Fog density
        glFogf(GL_FOG_DENSITY, inGameUI.getFogDensity());
        // Sensitivity is applied each frame in updateGame via InputHandler
    }

    private void transitionToGameOver(String message, boolean isWin) {
        gameOverMessage = message;
        gameOverIsWin = isWin;

        // Mark the active run as completed (win only — losses keep the run alive)
        if (isWin && activeRun != null && runManager != null) {
            long elapsed = runElapsedMs + (System.currentTimeMillis() - runStartTimeMs);
            runManager.markCompleted(activeRun, elapsed);
        }

        long currentWins = bestScoreCache;
        if (isWin) {
            int winsToAdd = (this.difficulty == Difficulty.HARD) ? 5 : 1;
            currentWins += winsToAdd;
            if (currentWins > bestScoreCache) {
                ScoreManager.saveBestScore(currentWins);
                bestScoreCache = currentWins;
            }
        }

        if (inGameUI != null) {
            inGameUI.setGameOverState(message, bestScoreCache);
            inGameUI.closeAdminPanel();
        }

        setCursorVisible(true);
        gameState = GameState.GAME_OVER;
    }

    // ============================================================
    // MAIN MENU STATE
    // ============================================================

    private void updateMainMenu() {
        if (inGameUI == null)
            return;

        // Check if a run was selected to start/continue
        RunData runToStart = inGameUI.getRunToStart();
        if (runToStart != null) {
            transitionToRun(runToStart);
            return;
        }

        // Check if the player just pressed CREATE on the Create Run screen.
        // InGameUI returns a stub (name + difficulty only); we create the real
        // run folder here, refresh the list, then start it.
        RunData newRunStub = inGameUI.getNewRunRequested();
        if (newRunStub != null) {
            RunData newRun = runManager.createRun(newRunStub.displayName, newRunStub.difficulty);
            inGameUI.refreshRunsList(runManager.loadAllRuns());
            transitionToRun(newRun);
            return;
        }

        if (inGameUI.isQuitRequested()) {
            isRunning = false;
            glfwSetWindowShouldClose(window, true);
        }
    }

    // ============================================================
    // PAUSED STATE
    // ============================================================

    private void updatePaused() {
        if (inGameUI == null)
            return;

        if (inGameUI.isPauseResumeRequested()) {
            transitionFromPaused();
            return;
        }

        if (inGameUI.isPauseQuitToMenuRequested()) {
            transitionToMainMenu();
        }
    }

    private void renderPaused() {
        // Render the 3D scene frozen behind the overlay
        int width, height;
        try (MemoryStack stack = stackPush()) {
            IntBuffer wBuf = stack.mallocInt(1);
            IntBuffer hBuf = stack.mallocInt(1);
            glfwGetWindowSize(window, wBuf, hBuf);
            width = wBuf.get(0);
            height = hBuf.get(0);
        }
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Frozen 3D scene
        if (world != null && player != null) {
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            perspective(FIELD_OF_VIEW, (float) width / height, NEAR_PLANE, FAR_PLANE);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            player.setupCamera();

            synchronized (world.getStaticObjects()) {
                for (GameObject obj : world.getStaticObjects()) {
                    obj.render();
                }
            }
            keyManager.render();
        }

        // 2D overlay
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glDisable(GL_DEPTH_TEST);

        inGameUI.renderPauseMenu(width, height);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);
        glEnable(GL_FOG);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    // ============================================================
    // MAIN MENU STATE
    // ============================================================

    private void renderMainMenu() {
        int width, height;
        try (MemoryStack stack = stackPush()) {
            IntBuffer wBuf = stack.mallocInt(1);
            IntBuffer hBuf = stack.mallocInt(1);
            glfwGetWindowSize(window, wBuf, hBuf);
            width = wBuf.get(0);
            height = hBuf.get(0);
        }
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Setup 2D ortho
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Disable 3D stuff for menu
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glDisable(GL_DEPTH_TEST);

        inGameUI.renderMainMenu(width, height);

        // Re-enable for gameplay
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);
        glEnable(GL_FOG);
    }

    // ============================================================
    // GAME OVER STATE
    // ============================================================

    private void updateGameOver() {
        if (inGameUI != null && inGameUI.isGameOverReturnToMenu()) {
            transitionToMainMenu();
        }
    }

    private void renderGameOver() {
        // Render the last game frame as background, then overlay
        int width, height;
        try (MemoryStack stack = stackPush()) {
            IntBuffer wBuf = stack.mallocInt(1);
            IntBuffer hBuf = stack.mallocInt(1);
            glfwGetWindowSize(window, wBuf, hBuf);
            width = wBuf.get(0);
            height = hBuf.get(0);
        }
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Render 3D scene frozen
        if (world != null && player != null) {
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            perspective(FIELD_OF_VIEW, (float) width / height, NEAR_PLANE, FAR_PLANE);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            player.setupCamera();

            synchronized (world.getStaticObjects()) {
                for (GameObject obj : world.getStaticObjects()) {
                    obj.render();
                }
            }
            keyManager.render();
        }

        // 2D overlay
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glDisable(GL_DEPTH_TEST);

        inGameUI.renderGameOver(width, height);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);
        glEnable(GL_FOG);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    // ============================================================
    // PLAYING STATE — GAME LOGIC
    // ============================================================

    /** Starts a new game with a fresh random seed. */
    public void startGame() {
        startGame(-1L);
    }

    /**
     * Starts a new game with the given seed. Pass {@code -1} to generate a new
     * random seed (same as calling {@link #startGame()}).
     */
    public void startGame(long seed) {
        currentScore = 0;
        bestScoreCache = ScoreManager.loadBestScore();

        // World Gen
        world = (seed == -1L)
            ? worldLoader.generateWorld(wallTextureID, orbTextureID, woodTextureID, sheetsTextureID)
            : worldLoader.generateWorld(wallTextureID, orbTextureID, woodTextureID, sheetsTextureID, seed);
        this.escapeDoor = world.getEscapeDoor();
        this.winTrigger = world.getWinTrigger();

        // Player & Keys
        player = new Player(0, 1.5f, 0);
        // Create a new InputHandler for this session
        inputHandler = new InputHandler(window);
        inputHandler.resetMouse();
        keyManager.initializeKeys(world.getAllRooms(), world.getStaticObjects(), TOTAL_KEYS);

        keysCollected = 0;
        adminKeys.clear();
        horrorLevel = 0.0f;

        // Pathfinding
        System.out.println("[GamePanel] Building DIY Pathfinding Grid for new world...");
        long startTime = System.currentTimeMillis();
        pathfinder = new PathfindingManager();
        pathfinder.buildGrid(world);
        long endTime = System.currentTimeMillis();
        System.out.println("[GamePanel] Pathfinding Grid built in " + (endTime - startTime) + " ms.");

        aiPath = null;
        aiPathIndex = 0;
        aiState = AiState.IDLE;
        aiFailedPathTimer = 0.0f;
        isAutoCollectActive = false;
        isFreeCamActive = false;
        isMapActive = false;

        // Objective
        allKeysCollectedMessageTriggered = false;
        popupMessage = "";
        popupMessageTimer = 0.0f;
        hotColdText = "";
        updateObjectiveText();

        // Start ambiance
        if (soundManager != null) {
            soundManager.loadAndLoopAmbiance();
        }
    }

    /** Saves the current in-flight run state to disk. */
    private void saveCurrentRunState() {
        if (activeRun == null || runManager == null || player == null || keyManager == null)
            return;
        List<Key> keys = keyManager.getKeys();
        boolean[] collected = new boolean[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            collected[i] = keys.get(i).collected;
        }
        long elapsed = runElapsedMs + (System.currentTimeMillis() - runStartTimeMs);
        RunState state = new RunState(
            worldLoader.getLastSeed(),
            player.getPosX(), player.getPosY(), player.getPosZ(),
            player.getYaw(), player.getPitch(),
            collected
        );
        runManager.saveRunState(activeRun, state);
        runManager.updateElapsed(activeRun, elapsed);
    }

    /** Opens the escape door (called when restoring a run where all keys were already collected). */
    private void openEscapeDoor() {
        allKeysCollectedMessageTriggered = true;
        if (escapeDoor != null) {
            escapeDoor.setCollidable(false);
            escapeDoor.setRendered(false);
            if (pathfinder != null) pathfinder.openDoorInGrid(escapeDoor);
        }
    }

    private void updateObjectiveText() {
        if (keysCollected < TOTAL_KEYS) {
            currentObjectiveText = "Objective: Find all keys (" + keysCollected + " / " + TOTAL_KEYS + ")";
        } else {
            currentObjectiveText = "Objective: Find the maze exit!";
        }
    }

    private void updateGame() {
        // Detect admin panel close-button usage (restores cursor/movement)
        if (inGameUI != null && inGameUI.wasAdminPanelJustClosed()) {
            setCursorVisible(false);
            if (inputHandler != null)
                inputHandler.resetMouse();
        }

        // Apply dynamic settings from pause menu
        if (inGameUI != null) {
            // Sensitivity
            inputHandler.setSensitivity(inGameUI.getSensitivity());
            // Invert Y
            inputHandler.invertY = inGameUI.isInvertY();
            // FOV & fog
            FIELD_OF_VIEW = inGameUI.getFieldOfView();
            glFogf(GL_FOG_DENSITY, inGameUI.getFogDensity());
            // Volume
            if (soundManager != null) {
                soundManager.setVolume(inGameUI.getMasterVolume());
            }
        }

        if (!isMapActive) {
            // Don't process player movement if admin panel is open with cursor
            if (!cursorVisible) {
                player.update(inputHandler, world, isFreeCamActive || isAutoCollectActive);
            }
        }

        // Key collection logic
        keyManager.update(player);
        int newKeysCollected = keyManager.getKeysCollected();
        if (newKeysCollected != keysCollected) {
            keysCollected = newKeysCollected;
            updateObjectiveText();
        }

        // Popup timer
        if (popupMessageTimer > 0) {
            popupMessageTimer -= FRAME_TIME_ESTIMATE;
            if (popupMessageTimer <= 0) {
                popupMessage = "";
            }
        }

        if (keysCollected == TOTAL_KEYS) {
            if (!allKeysCollectedMessageTriggered) {
                allKeysCollectedMessageTriggered = true;
                popupMessage = "NEW OBJECTIVE: FIND THE EXIT!";
                popupMessageTimer = POPUP_MESSAGE_DURATION;
            }

            if (escapeDoor != null && escapeDoor.isCollidable()) {
                escapeDoor.setCollidable(false);
                escapeDoor.setRendered(false);
                if (pathfinder != null) {
                    pathfinder.openDoorInGrid(escapeDoor);
                }
            }

            // Hot/Cold
            if (winTrigger != null && player != null) {
                float dx = winTrigger.getPosX() - player.getPosX();
                float dz = winTrigger.getPosZ() - player.getPosZ();
                float distanceSq = (dx * dx) + (dz * dz);

                if (distanceSq < 100.0f) {
                    hotColdText = "Burning Hot!";
                } else if (distanceSq < 400.0f) {
                    hotColdText = "Hot";
                } else if (distanceSq < 1600.0f) {
                    hotColdText = "Warm";
                } else if (distanceSq < 4900.0f) {
                    hotColdText = "Cold";
                } else {
                    hotColdText = "Freezing";
                }
            }
        } else {
            hotColdText = "";
        }

        // Win condition
        if (winTrigger != null) {
            float playerBodyY = player.getPosY() - player.getPlayerHalfHeight();
            float playerRadius = 0.3f;
            if (winTrigger.isColliding(player.getPosX(), playerBodyY, player.getPosZ(), playerRadius,
                    player.getPlayerHalfHeight())) {
                transitionToGameOver("YOU ESCAPED! Maze Champion! You win!", true);
                return;
            }
        }

        // AI timer
        if (aiFailedPathTimer > 0) {
            aiFailedPathTimer -= FRAME_TIME_ESTIMATE;
        }

        // AI logic
        if (isAutoCollectActive) {
            updateAutoCollectAI();
        } else {
            aiState = AiState.IDLE;
            aiPath = null;
        }

        // Horror
        if (!isFreeCamActive) {
            Room currentRoom = getPlayerCurrentRoom();
            if (currentRoom != null && currentRoom.getType() == RoomType.COURTYARD) {
                horrorLevel += HORROR_RATE_COURTYARD;
            } else {
                horrorLevel += HORROR_RATE_STANDARD;
            }

            if (horrorLevel >= 100.0f) {
                horrorLevel = 0.0f;
                if (soundManager != null) {
                    soundManager.playOneShot(CRACKLE_SOUND_FILE);
                }
            }
        }
    }

    private void updateAutoCollectAI() {
        float pX = player.getPosX();
        float pZ = player.getPosZ();

        switch (aiState) {
            case IDLE: {
                if (aiFailedPathTimer > 0) {
                    inputHandler.wPressed = false;
                    break;
                }

                float targetX, targetZ;
                boolean hasTarget = false;

                Key nearestKey = keyManager.findNearestKey(pX, pZ);
                if (nearestKey != null) {
                    targetX = nearestKey.x;
                    targetZ = nearestKey.z;
                    hasTarget = true;
                } else if (keysCollected == TOTAL_KEYS && winTrigger != null) {
                    targetX = winTrigger.getPosX();
                    targetZ = winTrigger.getPosZ();
                    hasTarget = true;
                } else {
                    inputHandler.wPressed = false;
                    return;
                }

                if (hasTarget) {
                    aiPath = pathfinder.findPath(pX, pZ, targetX, targetZ);
                    if (aiPath != null && aiPath.size() > 1) {
                        aiPathIndex = 1;
                        aiState = AiState.FOLLOWING_PATH;
                    } else {
                        aiPath = null;
                        aiState = AiState.PATH_FAILED;
                        aiFailedPathTimer = AI_RETRY_COOLDOWN;
                    }
                }
                break;
            }

            case FOLLOWING_PATH: {
                if (aiPath == null || aiPathIndex >= aiPath.size()) {
                    aiState = AiState.IDLE;
                    inputHandler.wPressed = false;
                    break;
                }

                PathNode targetNode = aiPath.get(aiPathIndex);
                float targetX = targetNode.worldX;
                float targetZ = targetNode.worldZ;

                float dx = targetX - pX;
                float dz = targetZ - pZ;

                if (dx * dx + dz * dz < 0.25f) {
                    aiPathIndex++;
                    inputHandler.wPressed = false;
                } else {
                    float angleToTarget = (float) Math.toDegrees(Math.atan2(dx, -dz));
                    float currentYaw = inputHandler.yaw;

                    float diff = angleToTarget - currentYaw;
                    while (diff < -180)
                        diff += 360;
                    while (diff > 180)
                        diff -= 360;

                    float turnSpeed = 10.0f;

                    if (Math.abs(diff) < turnSpeed) {
                        inputHandler.yaw = angleToTarget;
                    } else {
                        inputHandler.yaw += Math.signum(diff) * turnSpeed;
                    }

                    inputHandler.yaw = (inputHandler.yaw + 360) % 360;

                    if (Math.abs(diff) < 45) {
                        inputHandler.wPressed = true;
                    } else {
                        inputHandler.wPressed = false;
                    }
                }
                break;
            }

            case PATH_FAILED: {
                inputHandler.wPressed = false;
                if (aiFailedPathTimer <= 0) {
                    aiState = AiState.IDLE;
                }
                break;
            }
        }
    }

    private Room getPlayerCurrentRoom() {
        if (world == null || player == null) {
            return null;
        }
        float px = player.getPosX();
        float pz = player.getPosZ();
        for (Room room : world.getAllRooms()) {
            if (px > room.minX && px < room.maxX && pz > room.minZ && pz < room.maxZ) {
                return room;
            }
        }
        return null;
    }

    // ============================================================
    // PLAYING STATE — RENDERING
    // ============================================================

    private void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        int width, height;
        try (MemoryStack stack = stackPush()) {
            IntBuffer wBuf = stack.mallocInt(1);
            IntBuffer hBuf = stack.mallocInt(1);
            glfwGetWindowSize(window, wBuf, hBuf);
            width = wBuf.get(0);
            height = hBuf.get(0);
        }
        glViewport(0, 0, width, height);

        // 3D Projection
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        perspective(FIELD_OF_VIEW, (float) width / height, NEAR_PLANE, FAR_PLANE);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        player.setupCamera();

        // Render world
        synchronized (world.getStaticObjects()) {
            for (GameObject obj : world.getStaticObjects()) {
                obj.render();
            }
        }

        // Render keys
        keyManager.render();
        for (Key key : adminKeys) {
            key.rotation = 0;
            keyManager.renderKey(key);
        }

        // Debug renderer
        debugRenderer.render(isDebugLinesActive, isAutoCollectActive, player, keyManager, world, pathfinder);

        // 2D HUD
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        long displayBest = bestScoreCache;
        hudRenderer.render(width, height, keysCollected, TOTAL_KEYS, displayBest,
                isFreeCamActive, isAutoCollectActive, isDebugLinesActive,
                adminPanelFeatureAvailable, horrorLevel,
                currentObjectiveText, popupMessage, popupMessageTimer, hotColdText,
                pathfinder, player, keyManager, winTrigger, world, isMapActive);

        // Render admin panel overlay if open
        if (inGameUI != null && inGameUI.isAdminPanelOpen()) {
            glDisable(GL_LIGHTING);
            glDisable(GL_FOG);
            glDisable(GL_DEPTH_TEST);

            inGameUI.renderAdminPanel(width, height, new InGameUI.AdminCallback() {
                @Override
                public void onToggleAutoCollect(boolean active) {
                    setAutoCollectActive(active);
                }

                @Override
                public void onToggleDebugLines(boolean active) {
                    isDebugLinesActive = active;
                }

                @Override
                public void onSpawnObject(String shape, String textureName) {
                    int textureId = 0;
                    if (textureName != null && !textureName.equals("NONE (Color)")) {
                        textureId = loadedTextures.getOrDefault(textureName, 0);
                    }
                    addObjectAtPlayerPosition(shape, textureId);
                }

                @Override
                public void onTeleport(float x, float y, float z) {
                    teleportPlayer(x, y, z);
                }

                @Override
                public void onCopyCoords() {
                    float[] pos = getPlayerPosition();
                    String coords = String.format("(%.2f, %.2f, %.2f)", pos[0], pos[1], pos[2]);
                    try {
                        StringSelection stringSelection = new StringSelection(coords);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                    } catch (Exception e) {
                        System.err.println("Failed to copy to clipboard: " + e.getMessage());
                    }
                }
            });

            glEnable(GL_DEPTH_TEST);
            glEnable(GL_LIGHTING);
            glEnable(GL_FOG);
        }

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void perspective(float fov, float aspect, float near, float far) {
        float yMax = near * (float) Math.tan(Math.toRadians(fov / 2.0));
        float yMin = -yMax;
        float xMax = yMax * aspect;
        float xMin = -yMax * aspect;
        glFrustum(xMin, xMax, yMin, yMax, near, far);
    }

    // ============================================================
    // OBJECT SPAWNING
    // ============================================================

    public void addObjectAtPlayerPosition(String shapeType, int textureId) {
        float pX = player.getPosX();
        float pY = player.getPosY() - player.PLAYER_EYE_HEIGHT;
        float pZ = player.getPosZ();

        synchronized (world.getStaticObjects()) {
            switch (shapeType) {
                case "CUBE": {
                    GameObject newObj = new GameObject(ShapeType.CUBE, pX, pY, pZ, 1.0f, 1.0f, 1.0f, textureId);
                    world.getStaticObjects().add(newObj);
                    break;
                }
                case "SPHERE": {
                    GameObject newObj = new GameObject(ShapeType.SPHERE, pX, pY, pZ, 1.0f, 1.0f, 1.0f, textureId);
                    world.getStaticObjects().add(newObj);
                    break;
                }
                case "TABLE": {
                    float tableX = pX;
                    float tableZ = pZ;
                    float TABLE_TOP_Y = 0.8f;
                    float TABLE_TOP_W = 1.2f;
                    float TABLE_TOP_D = 0.8f;
                    float TABLE_TOP_H = 0.1f;
                    float LEG_HEIGHT = 0.8f;
                    float LEG_SIZE = 0.15f;
                    float LEG_OFFSET_X = (TABLE_TOP_W / 2.0f) - (LEG_SIZE / 2.0f);
                    float LEG_OFFSET_Z = (TABLE_TOP_D / 2.0f) - (LEG_SIZE / 2.0f);
                    int tableTextureId = (textureId != 0) ? textureId
                            : loadedTextures.getOrDefault("wood_texture.png", 0);

                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, tableX, TABLE_TOP_Y - (TABLE_TOP_H / 2.0f), tableZ,
                                    TABLE_TOP_W, TABLE_TOP_H, TABLE_TOP_D, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, tableX - LEG_OFFSET_X, 0.0f, tableZ - LEG_OFFSET_Z,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, tableX + LEG_OFFSET_X, 0.0f, tableZ - LEG_OFFSET_Z,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, tableX - LEG_OFFSET_X, 0.0f, tableZ + LEG_OFFSET_Z,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, tableX + LEG_OFFSET_X, 0.0f, tableZ + LEG_OFFSET_Z,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    break;
                }
                case "KEY": {
                    adminKeys.add(new Key(pX, pY, pZ));
                    break;
                }
                case "BED": {
                    float bedX = pX;
                    float bedZ = pZ;
                    float BED_WIDTH = 2.5f;
                    float BED_LENGTH = 3.0f;
                    float BED_HEIGHT = 0.4f;
                    float LEG_HEIGHT = 0.35f;
                    float LEG_SIZE = 0.15f;
                    float HEADBOARD_HEIGHT = 1.2f;
                    float HEADBOARD_THICKNESS = 0.1f;
                    float legOffsetX = (BED_LENGTH / 2.0f) - (LEG_SIZE / 2.0f);
                    float legOffsetZ = (BED_WIDTH / 2.0f) - (LEG_SIZE / 2.0f);
                    int woodTex = loadedTextures.getOrDefault("wood_texture.png", 0);
                    int sheetsTex = loadedTextures.getOrDefault("sheets_texture.png", 0);

                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, bedX - legOffsetX, 0.0f, bedZ - legOffsetZ,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, woodTex));
                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, bedX + legOffsetX, 0.0f, bedZ - legOffsetZ,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, woodTex));
                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, bedX - legOffsetX, 0.0f, bedZ + legOffsetZ,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, woodTex));
                    world.getStaticObjects()
                            .add(new GameObject(ShapeType.CUBE, bedX + legOffsetX, 0.0f, bedZ + legOffsetZ,
                                    LEG_SIZE, LEG_HEIGHT, LEG_SIZE, woodTex));
                    world.getStaticObjects().add(new GameObject(ShapeType.CUBE, bedX, LEG_HEIGHT, bedZ,
                            BED_LENGTH, BED_HEIGHT, BED_WIDTH, sheetsTex));
                    float headboardZ = bedZ + (BED_WIDTH / 2.0f) + (HEADBOARD_THICKNESS / 2.0f);
                    world.getStaticObjects().add(new GameObject(ShapeType.CUBE, bedX, LEG_HEIGHT, headboardZ,
                            BED_LENGTH, HEADBOARD_HEIGHT, HEADBOARD_THICKNESS, woodTex));
                    break;
                }
                case "TREE": {
                    float TRUNK_HEIGHT = 4.0f;
                    float TRUNK_WIDTH = 0.4f;
                    float LEAF_RADIUS = 2.0f;

                    GameObject trunk = new GameObject(
                            ShapeType.CUBE,
                            pX, 0.0f, pZ,
                            TRUNK_WIDTH, TRUNK_HEIGHT, TRUNK_WIDTH,
                            0.5f, 0.3f, 0.0f,
                            true, true);
                    float leafY = TRUNK_HEIGHT;
                    GameObject leaves = new GameObject(
                            ShapeType.SPHERE,
                            pX, leafY, pZ,
                            LEAF_RADIUS, LEAF_RADIUS, LEAF_RADIUS,
                            0.0f, 0.7f, 0.0f,
                            false, true);
                    world.getStaticObjects().add(trunk);
                    world.getStaticObjects().add(leaves);
                    break;
                }
                case "ESCAPE_DOOR": {
                    float doorW = 4.0f;
                    float doorH = 3.0f;
                    float doorD = 0.1f;
                    GameObject door = new GameObject(
                            ShapeType.CUBE,
                            pX, 0.0f, pZ,
                            doorW, doorH, doorD,
                            0.5f, 0.3f, 0.0f,
                            true, true);
                    world.getStaticObjects().add(door);
                    break;
                }
                default:
                    GameObject newObj = new GameObject(ShapeType.CUBE, pX, pY, pZ, 1.0f, 1.0f, 1.0f, textureId);
                    world.getStaticObjects().add(newObj);
                    break;
            }
        }
        System.out.println(
                "Spawned " + shapeType + " with Texture ID " + textureId + " at (" + pX + ", " + pY + ", " + pZ + ")");
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public void stopGame() {
        isRunning = false;
    }

    private void dispose() {
        if (soundManager != null) {
            soundManager.stopAmbiance();
        }
        hudRenderer.cleanup();
        if (inGameUI != null)
            inGameUI.cleanup();

        for (int id : loadedTextures.values()) {
            glDeleteTextures(id);
        }

        Callbacks.glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null)
            cb.free();
    }

    // ============================================================
    // GETTERS / SETTERS (used by InGameUI admin panel)
    // ============================================================

    public boolean isAutoCollectActive() {
        return isAutoCollectActive;
    }

    public void setAutoCollectActive(boolean active) {
        this.isAutoCollectActive = active;
        if (active) {
            this.isFreeCamActive = false;
            this.aiState = AiState.IDLE;
            this.aiFailedPathTimer = 0.0f;
        } else {
            if (inputHandler != null)
                inputHandler.wPressed = false;
        }
    }

    public void setFreeCamActive(boolean active) {
        this.isFreeCamActive = active;
    }

    public boolean isDebugLinesActive() {
        return isDebugLinesActive;
    }

    public void setDebugLinesActive(boolean active) {
        this.isDebugLinesActive = active;
    }

    public boolean isDebugLinesFeatureAvailable() {
        return debugLinesFeatureAvailable;
    }

    public float[] getGroundColor() {
        return new float[] { groundR, groundG, groundB };
    }

    public void setGroundColor(float r, float g, float b) {
        this.groundR = r;
        this.groundG = g;
        this.groundB = b;
    }

    public float[] getPlayerPosition() {
        if (player != null) {
            return new float[] { player.getPosX(), player.getPosY(), player.getPosZ() };
        }
        return new float[] { 0.0f, 0.0f, 0.0f };
    }

    public void teleportPlayer(float x, float y, float z) {
        if (player != null) {
            player.teleportTo(x, y, z);
        }
    }

    public Map<String, Integer> getLoadedTextures() {
        return Collections.unmodifiableMap(loadedTextures);
    }

    public int getTextureIDByName(String name) {
        return loadedTextures.getOrDefault(name, 0);
    }

    public int getOrbTextureID() {
        return orbTextureID;
    }

    public int getWallTextureID() {
        return wallTextureID;
    }

    public int getWoodTextureID() {
        return woodTextureID;
    }

    public int getSheetsTextureID() {
        return sheetsTextureID;
    }
}
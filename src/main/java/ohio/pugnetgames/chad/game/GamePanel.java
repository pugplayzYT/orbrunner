package ohio.pugnetgames.chad.game;

import ohio.pugnetgames.chad.GameApp;
import ohio.pugnetgames.chad.core.BuildManager;
import ohio.pugnetgames.chad.core.ScoreManager;
import ohio.pugnetgames.chad.core.SoundManager;
import ohio.pugnetgames.chad.core.Difficulty; // <-- IMPORT NEW ENUM
import ohio.pugnetgames.chad.game.GameObject.ShapeType;
import ohio.pugnetgames.chad.game.Room.RoomType;

// --- 💥FIX: Use our OWN PathNode class ---
import ohio.pugnetgames.chad.game.PathNode;
import java.util.List;
// --- 💥END FIX ---

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GamePanel (LwjglGame) handles the main game loop, window,
 * and orchestrates all the game systems (Player, World, Keys, UI).
 * NEW: Added DIY A* Pathfinding AI.
 * NEW: Added Objectives & Hot/Cold System.
 *
 * MODIFIED: Now accepts a Difficulty setting.
 */
public class GamePanel extends Thread {

    private final GameApp app;
    private long window;
    private volatile boolean isRunning;
    private long currentScore; // Now tracks WINS
    private long bestScoreCache; // Now tracks BEST WINS

    // --- GAME FIELDS ---
    // --- MODIFIED: TOTAL_KEYS is no longer final, set by difficulty ---
    private int TOTAL_KEYS;
    private int keysCollected = 0;
    private GameObject escapeDoor;
    private GameObject winTrigger;
    private List<Key> adminKeys = new ArrayList<>();
    private SoundManager soundManager;

    // --- HORROR FIELDS ---
    private float horrorLevel = 0.0f;
    // --- 💥 MODIFICATION: Tuned horror rates (4x original) ---
    private final float HORROR_RATE_STANDARD = 0.02f; // Was 0.005f
    private final float HORROR_RATE_COURTYARD = 0.004f; // Was 0.001f
    // --- 💥 END MODIFICATION ---
    private final String CRACKLE_SOUND_FILE = "crackle.mp3";

    // --- 💥FIX: RE-ADDED OBJECTIVE / HUD FIELDS ---
    private String currentObjectiveText = "";
    private String popupMessage = "";
    private float popupMessageTimer = 0.0f;
    private final float POPUP_MESSAGE_DURATION = 4.0f; // 4 seconds
    private final float FRAME_TIME_ESTIMATE = 0.0166f; // Assuming 60fps for timer
    private boolean allKeysCollectedMessageTriggered = false;
    private String hotColdText = "";
    // --- 💥END FIX ---

    // --- GAME SYSTEMS ---
    private World world;
    private Player player;
    private InputHandler inputHandler;
    private KeyManager keyManager;
    private HudRenderer hudRenderer;
    private DebugRenderer debugRenderer;
    private WorldLoader worldLoader;

    // --- 💥FIX: DIY A* PATHFINDING AI ---
    private PathfindingManager pathfinder;
    private List<PathNode> aiPath; // Note: This is OUR PathNode
    private int aiPathIndex;
    // 💥FIX: Added PATH_FAILED state to prevent spam
    private enum AiState { IDLE, FINDING_PATH, FOLLOWING_PATH, PATH_FAILED }
    private AiState aiState = AiState.IDLE;
    // 💥FIX: Timer for failed state
    private float aiFailedPathTimer = 0.0f;
    private final float AI_RETRY_COOLDOWN = 3.0f; // 3 seconds
    // --- 💥END FIX ---

    // --- Textures ---
    private int orbTextureID;
    private int wallTextureID;

    // --- Game State ---
    private final float FIELD_OF_VIEW = 60.0f;
    private final float NEAR_PLANE = 0.1f;
    private final float FAR_PLANE = 100.0f;

    private boolean freeCamFeatureAvailable = false;
    private volatile boolean isFreeCamActive = false;
    private boolean adminPanelFeatureAvailable = false;
    private volatile boolean isAutoCollectActive = false;
    private volatile float groundR = 0.1f, groundG = 0.5f, groundB = 0.2f;
    private AdminPanelUI adminPanelUI;
    private boolean debugLinesFeatureAvailable = false;
    private volatile boolean isDebugLinesActive = false;
    // --- 💥 NEW: Full Map State ---
    private volatile boolean isMapActive = false;
    // --- 💥 END NEW ---

    // --- NEW: Difficulty State ---
    private final Difficulty difficulty;

    // --- MODIFIED: Constructor now accepts Difficulty ---
    public GamePanel(GameApp app, Difficulty difficulty) {
        this.app = app;
        this.difficulty = difficulty; // Store the difficulty
    }

    @Override
    public void run() {
        init();
        loop();
        dispose();
    }

    private void init() {
        // ... (All the GLFW and window setup code is the same) ...
        this.freeCamFeatureAvailable = BuildManager.getBoolean("feature.freecam.enabled");
        this.adminPanelFeatureAvailable = BuildManager.getBoolean("feature.adminpanel.enabled");
        this.debugLinesFeatureAvailable = BuildManager.getBoolean("feature.debuglines.enabled");

        // --- NEW: Set TOTAL_KEYS based on difficulty ---
        if (this.difficulty == Difficulty.HARD) {
            this.TOTAL_KEYS = 10;
        } else {
            this.TOTAL_KEYS = 3; // Default to Easy
        }
        // --- END NEW ---

        if (adminPanelFeatureAvailable) {
            SwingUtilities.invokeLater(() -> adminPanelUI = new AdminPanelUI(this));
        }
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
        window = glfwCreateWindow(width, height, "TRUE OpenGL Maze Escape (LWJGL)", primaryMonitor, NULL);
        if (window == NULL) {
            System.err.println("FATAL: Failed to create window, trying fallback.");
            width = 800; height = 600;
            window = glfwCreateWindow(width, height, "TRUE OpenGL Maze Escape (LWJGL)", NULL, NULL);
            if (window == NULL) {
                System.err.println("FATAL: Failed to create window in fallback mode.");
                return;
            }
        }
        inputHandler = new InputHandler(window);
        glfwSetKeyCallback(window, this::gameStateKeyCallback);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();
        orbTextureID = TextureLoader.loadTexture("orb_texture.png");
        wallTextureID = TextureLoader.loadTexture("tunnel_texture.png");
        worldLoader = new WorldLoader();
        // --- Init Game Systems (World gen is now in startGame) ---
        player = new Player(0, 1.5f, 0);
        keyManager = new KeyManager();
        hudRenderer = new HudRenderer();
        debugRenderer = new DebugRenderer();
        soundManager = new SoundManager();
        soundManager.loadAndLoopAmbiance();
        hudRenderer.init();

        // ... (OpenGL state setup: glClearColor, glEnable, lighting, fog... all unchanged) ...
        // --- 💥 FLASHLIGHT REMOVAL: Keep background black for fog ---
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        // --- 💥 END MOD ---

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_LIGHTING);

        // --- 💥 FLASHLIGHT REMOVAL: Turn OFF the flashlight (GL_LIGHT0) ---
        glDisable(GL_LIGHT0);

        glEnable(GL_NORMALIZE);
        glShadeModel(GL_SMOOTH);
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);

        // --- 💥 FLASHLIGHT REMOVAL: Make global ambient light BRIGHT ---
        // Global ambient light (the "minimum" light everywhere)
        float[] globalAmbient = {0.8f, 0.8f, 0.8f, 1.0f}; // Bright, uniform light
        glLightModelfv(GL_LIGHT_MODEL_AMBIENT, globalAmbient);

        // --- 💥 FLASHLIGHT REMOVAL: Deleted all GL_LIGHT0 properties (ambient, diffuse, attenuation, etc.) ---


        // --- 💥 FOG: Keep the fog enabled ---
        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_EXP2); // Exponential fog, very spooky

        glFogfv(GL_FOG_COLOR, new float[]{0.0f, 0.0f, 0.0f, 1.0f}); // Black fog
        glFogf(GL_FOG_DENSITY, 0.07f); // How thick the fog is (tweak this!)
        // --- 💥 END MODS ---

        startGame();
    }

    private void gameStateKeyCallback(long window, int key, int scode, int action, int mods) {
        inputHandler.keyCallback.invoke(window, key, scode, action, mods);

        if (action == GLFW_RELEASE) {
            if (key == GLFW_KEY_ESCAPE) {
                stopGame("Game exited.", false);
                glfwSetWindowShouldClose(window, true);
            }
            // --- 💥 NEW: Map Toggle Key (M) ---
            else if (key == GLFW_KEY_M) {
                isMapActive = !isMapActive;
                // If map is active, clear other states that prevent movement
                if (isMapActive) {
                    isFreeCamActive = false;
                    isAutoCollectActive = false;
                    // Also clear player movement input immediately
                    inputHandler.wPressed = false;
                    inputHandler.sPressed = false;
                    inputHandler.aPressed = false;
                    inputHandler.dPressed = false;
                    System.out.println("Full Map Toggled: " + isMapActive);
                }
            }
            // --- 💥 END NEW ---
            else if (key == GLFW_KEY_P) {
                if (freeCamFeatureAvailable) {
                    isFreeCamActive = !isFreeCamActive;
                    System.out.println("Free Cam Toggled: " + isFreeCamActive);
                    if (isFreeCamActive) {
                        isAutoCollectActive = false;
                        aiState = AiState.IDLE;
                        inputHandler.wPressed = false;

                        SwingUtilities.invokeLater(() -> {
                            if (adminPanelUI != null) adminPanelUI.syncToGameState();
                        });
                    }
                }
            }
            else if (key == GLFW_KEY_F2) {
                if (adminPanelFeatureAvailable && adminPanelUI != null) {
                    SwingUtilities.invokeLater(() -> {
                        if (adminPanelUI.isVisible()) {
                            adminPanelUI.setVisible(false);
                        } else {
                            adminPanelUI.syncToGameState(); // Sync *before* showing
                            adminPanelUI.setVisible(true);
                        }
                    });
                }
            }
        }
    }


    private void loop() {
        while (!glfwWindowShouldClose(window) && isRunning) {
            updateGame();
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    public void startGame() {
        isRunning = true;
        currentScore = 0;
        bestScoreCache = ScoreManager.loadBestScore();

        // --- World Gen ---
        world = worldLoader.generateWorld(wallTextureID, orbTextureID);
        this.escapeDoor = world.getEscapeDoor();
        this.winTrigger = world.getWinTrigger();

        // --- Player & Keys ---
        player = new Player(0, 1.5f, 0);
        inputHandler.resetMouse();
        // --- MODIFIED: Pass TOTAL_KEYS to KeyManager ---
        keyManager.initializeKeys(world.getAllRooms(), world.getStaticObjects(), TOTAL_KEYS);

        keysCollected = 0;
        adminKeys.clear();
        horrorLevel = 0.0f;

        // --- 💥FIX: DIY Pathfinding Grid Build ---
        System.out.println("[GamePanel] Building DIY Pathfinding Grid for new world...");
        long startTime = System.currentTimeMillis();
        pathfinder = new PathfindingManager();
        pathfinder.buildGrid(world); // Build grid based on the world
        long endTime = System.currentTimeMillis();
        System.out.println("[GamePanel] Pathfinding Grid built in " + (endTime - startTime) + " ms.");

        // Reset AI state
        aiPath = null;
        aiPathIndex = 0;
        aiState = AiState.IDLE;
        aiFailedPathTimer = 0.0f; // 💥FIX: Reset timer
        isAutoCollectActive = false;
        // --- 💥END FIX ---

        // --- 💥FIX: RE-ADD OBJECTIVE RESET ---
        allKeysCollectedMessageTriggered = false;
        popupMessage = "";
        popupMessageTimer = 0.0f;
        hotColdText = "";
        updateObjectiveText(); // Set initial objective
        // --- 💥END FIX ---
    }

    // --- 💥FIX: RE-ADD OBJECTIVE HELPER ---
    /**
     * Helper method to update the objective text based on game state.
     */
    private void updateObjectiveText() {
        if (keysCollected < TOTAL_KEYS) {
            currentObjectiveText = "Objective: Find all keys (" + keysCollected + " / " + TOTAL_KEYS + ")";
        } else {
            currentObjectiveText = "Objective: Find the maze exit!";
        }
    }
    // --- 💥END FIX ---

    private void updateGame() {
        // ... (player.update, keyManager.update logic is unchanged) ...
        // Player update now includes AI movement *or* player movement
        // --- 💥 MODIFIED: Disable player update if map is full screen 💥 ---
        if (!isMapActive) {
            player.update(inputHandler, world, isFreeCamActive || isAutoCollectActive);
        }

        // Key collection logic
        keyManager.update(player);
        // 💥FIX: Check for key collection *changes* to update objective text
        int newKeysCollected = keyManager.getKeysCollected();
        if (newKeysCollected != keysCollected) {
            keysCollected = newKeysCollected;
            updateObjectiveText(); // Update text when count changes
        }
        // --- 💥END FIX ---

        // --- 💥FIX: RE-ADD POPUP AND HOT/COLD LOGIC ---
        // Update popup timer
        if (popupMessageTimer > 0) {
            popupMessageTimer -= FRAME_TIME_ESTIMATE; // HACK: Assumes 60fps
            if (popupMessageTimer <= 0) {
                popupMessage = ""; // Clear message when timer expires
            }
        }

        if (keysCollected == TOTAL_KEYS) {
            // Trigger popup message ONCE
            if (!allKeysCollectedMessageTriggered) {
                allKeysCollectedMessageTriggered = true;
                popupMessage = "NEW OBJECTIVE: FIND THE EXIT!";
                popupMessageTimer = POPUP_MESSAGE_DURATION;
            }

            // Open escape door
            if (escapeDoor != null && escapeDoor.isCollidable()) {
                escapeDoor.setCollidable(false);
                escapeDoor.setRendered(false);

                // --- 💥💥💥 THE FIX 💥💥💥 ---
                // Tell the pathfinder the door is open so the grid is no longer blocked!
                if (pathfinder != null) {
                    pathfinder.openDoorInGrid(escapeDoor);
                }
                // --- 💥💥💥 END FIX 💥💥💥 ---
            }

            // Update Hot/Cold text
            if (winTrigger != null && player != null) {
                float dx = winTrigger.getPosX() - player.getPosX();
                float dz = winTrigger.getPosZ() - player.getPosZ();
                float distanceSq = (dx * dx) + (dz * dz);

                if (distanceSq < 100.0f) { hotColdText = "Burning Hot!"; }
                else if (distanceSq < 400.0f) { hotColdText = "Hot"; }
                else if (distanceSq < 1600.0f) { hotColdText = "Warm"; }
                else if (distanceSq < 4900.0f) { hotColdText = "Cold"; }
                else { hotColdText = "Freezing"; }
            }
        } else {
            hotColdText = ""; // Not active until all keys are found
        }
        // --- 💥END FIX ---


        // Win condition is the same
        if (winTrigger != null) {
            float playerBodyY = player.getPosY() - player.getPlayerHalfHeight();
            float playerRadius = 0.3f;
            if (winTrigger.isColliding(player.getPosX(), playerBodyY, player.getPosZ(), playerRadius, player.getPlayerHalfHeight())) {
                stopGame("YOU ESCAPED! Maze Champion! You win!", true);
                glfwSetWindowShouldClose(window, true);
                return;
            }
        }

        // ... (AI logic: aiFailedPathTimer, updateAutoCollectAI... all unchanged) ...
        // --- 💥FIX: Tick AI timer ---
        if (aiFailedPathTimer > 0) {
            aiFailedPathTimer -= FRAME_TIME_ESTIMATE;
        }

        // --- 💥FIX: DIY A* AI LOGIC ---
        if (isAutoCollectActive) {
            updateAutoCollectAI();
        } else {
            // If AI is turned off, reset its state
            aiState = AiState.IDLE;
            aiPath = null;
        }
        // --- 💥END FIX ---

        // Horror logic is the same
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

    /**
     * 💥FIX: State machine for the Auto-Collect DIY AI.
     * NOW WITH SPAM-PREVENTION (PATH_FAILED state).
     */
    private void updateAutoCollectAI() {
        float pX = player.getPosX();
        float pZ = player.getPosZ();

        switch (aiState) {
            case IDLE: {
                // 💥FIX: Check if we are on cooldown from a failed path
                if (aiFailedPathTimer > 0) {
                    inputHandler.wPressed = false;
                    break; // Don't try to find a new path yet
                }

                // AI is idle, it needs a new task.
                // 1. Find a target.
                float targetX, targetZ;
                boolean hasTarget = false;

                Key nearestKey = keyManager.findNearestKey(pX, pZ);
                if (nearestKey != null) {
                    targetX = nearestKey.x;
                    targetZ = nearestKey.z;
                    hasTarget = true;
                    System.out.println("[AI] New target: Key at (" + targetX + ", " + targetZ + ")");
                } else if (keysCollected == TOTAL_KEYS && winTrigger != null) {
                    targetX = winTrigger.getPosX();
                    targetZ = winTrigger.getPosZ();
                    hasTarget = true;
                    System.out.println("[AI] New target: Win Trigger at (" + targetX + ", " + targetZ + ")");
                } else {
                    // No keys, no exit? Stop.
                    inputHandler.wPressed = false;
                    return;
                }

                // 2. Find a path to the target.
                if (hasTarget) {
                    aiPath = pathfinder.findPath(pX, pZ, targetX, targetZ);
                    if (aiPath != null && aiPath.size() > 1) {
                        // Path found! Start following at the next node (index 1)
                        aiPathIndex = 1;
                        aiState = AiState.FOLLOWING_PATH;
                        System.out.println("[AI] Path found. State -> FOLLOWING_PATH");
                    } else {
                        // 💥FIX: Path failed! Go to new state.
                        System.err.println("[AI] No path found to target. Entering FAILED state.");
                        aiPath = null;
                        aiState = AiState.PATH_FAILED;
                        aiFailedPathTimer = AI_RETRY_COOLDOWN;
                    }
                }
                break;
            }

            case FOLLOWING_PATH: {
                // AI is following a pre-calculated path.
                if (aiPath == null || aiPathIndex >= aiPath.size()) {
                    // We either finished the path or it's invalid. Go back to IDLE.
                    aiState = AiState.IDLE;
                    inputHandler.wPressed = false;
                    System.out.println("[AI] Path finished or invalid. State -> IDLE");
                    break;
                }

                // Get the next node in the path
                PathNode targetNode = aiPath.get(aiPathIndex);
                float targetX = targetNode.worldX;
                float targetZ = targetNode.worldZ;

                // Calculate vector to the target node
                float dx = targetX - pX;
                float dz = targetZ - pZ;

                // 💥FIX: Stricter "close enough" check. Radius is 0.5f (0.25f squared)
                // This stops the AI from cutting corners.
                if (dx * dx + dz * dz < 0.25f) {
                    // We are "at" the node. Move to the next one.
                    aiPathIndex++;
                    System.out.println("[AI] Reached path node. Moving to index " + aiPathIndex);
                    // Don't move for one frame, to allow turning
                    inputHandler.wPressed = false;
                } else {
                    // 💥FIX: Smooth Turning Logic
                    float angleToTarget = (float) Math.toDegrees(Math.atan2(dx, -dz));
                    float currentYaw = inputHandler.yaw;

                    // Find the shortest angle difference
                    float diff = angleToTarget - currentYaw;
                    while (diff < -180) diff += 360;
                    while (diff > 180) diff -= 360;

                    // Define a turn speed (e.g., 10 degrees per frame)
                    float turnSpeed = 10.0f;

                    if (Math.abs(diff) < turnSpeed) {
                        // If we're close, snap to the target angle
                        inputHandler.yaw = angleToTarget;
                    } else {
                        // Otherwise, turn smoothly
                        inputHandler.yaw += Math.signum(diff) * turnSpeed;
                    }

                    // Normalize yaw
                    inputHandler.yaw = (inputHandler.yaw + 360) % 360;

                    // Only move forward if we are generally facing the target
                    if (Math.abs(diff) < 45) {
                        inputHandler.wPressed = true;
                    } else {
                        inputHandler.wPressed = false; // Stop to turn
                    }
                }
                break;
            }

            // 💥FIX: New state to handle failed paths and prevent spam
            case PATH_FAILED: {
                // We are waiting for the timer to run out.
                inputHandler.wPressed = false;
                if (aiFailedPathTimer <= 0) {
                    System.out.println("[AI] Cooldown finished. State -> IDLE");
                    aiState = AiState.IDLE;
                }
                break;
            }
        }
    }

    /**
     * Helper method to find which room the player is currently in.
     */
    private Room getPlayerCurrentRoom() {
        // ... (This method is unchanged) ...
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


    private void render() {
        // ... (GL setup, projection, modelview... all unchanged) ...
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

        // --- Setup Projection ---
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        perspective(FIELD_OF_VIEW, (float) width / height, NEAR_PLANE, FAR_PLANE);

        // --- Setup ModelView ---
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();


        // --- 💥💥💥 FLASHLIGHT REMOVAL 💥💥💥 ---
        // We deleted the flashlight logic block.
        // We just set up the camera, and that's it.
        // The global ambient light from init() does all the work.
        player.setupCamera();
        // --- 💥💥💥 END FIX 💥💥💥 ---


        // Render world objects
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

        // --- 💥FIX: Pass 'world' object to DebugRenderer ---
        debugRenderer.render(isDebugLinesActive, isAutoCollectActive, player, keyManager, world, pathfinder);
        // --- 💥END FIX ---

        // 2D HUD rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        long displayBest = bestScoreCache;
        // --- 💥 MODIFIED: Pass isMapActive to HudRenderer.render (20 args) 💥 ---
        // --- NOTE: This call is correct, TOTAL_KEYS is a field read by this method ---
        hudRenderer.render(width, height, keysCollected, TOTAL_KEYS, displayBest,
                isFreeCamActive, isAutoCollectActive, isDebugLinesActive,
                adminPanelFeatureAvailable, horrorLevel,
                currentObjectiveText, popupMessage, popupMessageTimer, hotColdText,
                pathfinder, player, keyManager, winTrigger, world, isMapActive); // ADDED isMapActive
        // --- 💥 END MODIFIED ---

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void perspective(float fov, float aspect, float near, float far) {
        // ... (This method is unchanged) ...
        float yMax = near * (float) Math.tan(Math.toRadians(fov / 2.0));
        float yMin = -yMax;
        float xMax = yMax * aspect;
        float xMin = -yMax * aspect;
        glFrustum(xMin, xMax, yMin, yMax, near, far);
    }

    public void addObjectAtPlayerPosition(String shapeType, int textureId) {
        // ... (This method is unchanged) ...
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
                    world.getStaticObjects().add(new GameObject(ShapeType.CUBE, tableX, TABLE_TOP_Y - (TABLE_TOP_H / 2.0f), tableZ,
                            TABLE_TOP_W, TABLE_TOP_H, TABLE_TOP_D, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects().add(new GameObject(ShapeType.CUBE, tableX - LEG_OFFSET_X, 0.0f, tableZ - LEG_OFFSET_Z,
                            LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects().add(new GameObject(ShapeType.CUBE, tableX + LEG_OFFSET_X, 0.0f, tableZ - LEG_OFFSET_Z,
                            LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects().add(new GameObject(ShapeType.CUBE, tableX - LEG_OFFSET_X, 0.0f, tableZ + LEG_OFFSET_Z,
                            LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    world.getStaticObjects().add(new GameObject(ShapeType.CUBE, tableX + LEG_OFFSET_X, 0.0f, tableZ + LEG_OFFSET_Z,
                            LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                    break;
                }
                case "KEY": {
                    adminKeys.add(new Key(pX, pY, pZ));
                    break;
                }
            }
        }
        System.out.println("Spawned " + shapeType + " at (" + pX + ", " + pY + ", " + pZ + ")");
    }

    public void stopGame() {
        stopGame("Game exited.", false);
    }

    private void stopGame(String message, boolean isWin) {
        // ... (This part is mostly unchanged) ...
        if (!isRunning) return;
        isRunning = false;
        if (adminPanelUI != null) {
            SwingUtilities.invokeLater(() -> adminPanelUI.dispose());
        }
        long currentWins = bestScoreCache;
        if (isWin) {
            // --- MODIFICATION: Add wins based on difficulty ---
            int winsToAdd = (this.difficulty == Difficulty.HARD) ? 5 : 1;
            currentWins += winsToAdd;
            // --- END MODIFICATION ---

            if (currentWins > bestScoreCache) {
                ScoreManager.saveBestScore(currentWins);
                bestScoreCache = currentWins;
            }
        }
        final long finalWins = bestScoreCache;
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(app,
                    message +
                            "\n\nTotal Wins: " + finalWins,
                    "Game Over (LWJGL)",
                    JOptionPane.INFORMATION_MESSAGE);
            app.setVisible(true);
            app.showMenu();
        });
    }

    private void dispose() {
        // ... (This method is unchanged) ...
        if (soundManager != null) {
            soundManager.stopAmbiance();
        }
        hudRenderer.cleanup();
        glDeleteTextures(orbTextureID);
        glDeleteTextures(wallTextureID);
        Callbacks.glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    // --- Swing Communication Getters/Setters ---
    // (These are all unchanged, but setAutoCollectActive has AI state reset)
    public boolean isAutoCollectActive() { return isAutoCollectActive; }
    public void setAutoCollectActive(boolean active) {
        this.isAutoCollectActive = active;
        if (active) {
            this.isFreeCamActive = false;
            this.aiState = AiState.IDLE; // Reset AI when toggled
            this.aiFailedPathTimer = 0.0f; // 💥FIX: Reset timer
        } else {
            inputHandler.wPressed = false; // Stop AI from walking
        }
    }
    public void setFreeCamActive(boolean active) { this.isFreeCamActive = active; }
    public boolean isDebugLinesActive() { return isDebugLinesActive; }
    public void setDebugLinesActive(boolean active) { this.isDebugLinesActive = active; }
    public boolean isDebugLinesFeatureAvailable() { return debugLinesFeatureAvailable; }
    public float[] getGroundColor() { return new float[]{groundR, groundG, groundB}; }
    public void setGroundColor(float r, float g, float b) {
        this.groundR = r;
        this.groundG = g;
        this.groundB = b;
    }
    public float[] getPlayerPosition() {
        if (player != null) {
            return new float[]{player.getPosX(), player.getPosY(), player.getPosZ()};
        }
        return new float[]{0.0f, 0.0f, 0.0f};
    }
    public void teleportPlayer(float x, float y, float z) {
        if (player != null) {
            player.teleportTo(x, y, z);
        }
    }
    public int getOrbTextureID() {
        return orbTextureID;
    }
    public int getWallTextureID() {
        return wallTextureID;
    }
}
package ohio.pugnetgames.chad.game;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Manages player state, movement, physics, and collision.
 */
public class Player {

    // Player/Camera state
    private float posX, posY, posZ;
    private float prevPosX, prevPosZ;
    private float velY;
    private float yaw, pitch;
    private boolean onGround = false;

    // Constants
    // --- 💥 FIX: Reduced base speed from 0.10f to 0.05f 💥 ---
    private final float CAMERA_SPEED = 0.05f;
    // --- 徴 NEW: Sprint Speed Multiplier ---
    // --- 💥 FIX: Reduced sprint multiplier from 1.8f to 1.5f 💥 ---
    private final float SPRINT_MULTIPLIER = 1.5f;
    // --- 徴 END NEW ---
    private final float GRAVITY = 0.005f;
    private final float JUMP_POWER = 0.15f;
    public final float PLAYER_EYE_HEIGHT = 1.5f;
    private final float PLAYER_COLLISION_RADIUS = 0.3f;
    private final float PLAYER_HALF_HEIGHT = 0.5f; // Player body is 1.0f high

    public Player(float startX, float startY, float startZ) {
        this.posX = startX;
        this.posY = startY;
        this.posZ = startZ;
        this.velY = 0;
        this.yaw = 0;
        this.pitch = 0;
    }

    /**
     * Updates player position based on input, gravity, and collisions.
     * @param input The InputHandler to read key states from.
     * @param world The World to check for collisions against.
     * @param isFreeCam Whether free-cam is active.
     */
    public void update(InputHandler input, World world, boolean isFreeCam) {
        // 1. Get Mouse Look
        this.yaw = input.yaw;
        this.pitch = input.pitch;

        // 2. Save previous position
        prevPosX = posX;
        prevPosZ = posZ;

        // 3. Calculate intended horizontal movement
        float dx = 0, dz = 0;
        float sinYaw = (float) Math.sin(Math.toRadians(yaw));
        float cosYaw = (float) Math.cos(Math.toRadians(yaw));

        // --- 徴 MODIFIED: Use base camera speed here ---
        if (input.wPressed) { dx += CAMERA_SPEED * sinYaw; dz -= CAMERA_SPEED * cosYaw; }
        if (input.sPressed) { dx -= CAMERA_SPEED * sinYaw; dz += CAMERA_SPEED * cosYaw; }
        if (input.aPressed) { dx -= CAMERA_SPEED * cosYaw; dz -= CAMERA_SPEED * sinYaw; }
        if (input.dPressed) { dx += CAMERA_SPEED * cosYaw; dz += CAMERA_SPEED * sinYaw; }
        // --- 徴 END MODIFICATION ---

        if (isFreeCam) {
            // Free-cam logic
            velY = 0;
            onGround = false;
            if (input.spacePressed) { posY += CAMERA_SPEED; }
            if (input.shiftPressed) { posY -= CAMERA_SPEED; } // This is correct for free-cam
            posX += dx;
            posZ += dz;
        } else {
            // --- 徴 NEW: SPRINT LOGIC ---
            // Apply sprint multiplier ONLY if not in free-cam
            if (input.shiftPressed) {
                dx *= SPRINT_MULTIPLIER;
                dz *= SPRINT_MULTIPLIER;
            }
            // --- 徴 END NEW ---

            // Physics/Collision logic
            float nextPosX = posX + dx;
            float nextPosZ = posZ + dz;

            // --- Y-AXIS MOVEMENT ---
            float nextPosY = posY;
            if (input.spacePressed && onGround) {
                velY = JUMP_POWER;
                onGround = false;
            }
            velY -= GRAVITY;
            nextPosY += velY;

            onGround = false;
            float playerCenterY = nextPosY - PLAYER_HALF_HEIGHT;
            float prevPlayerCenterY = posY - PLAYER_HALF_HEIGHT;

            // Wall/Object Collision
            for (GameObject obj : world.getStaticObjects()) {
                if (obj.getShape() == GameObject.ShapeType.CUBE && obj.isCollidable()) {

                    // 1. Resolve Y
                    if (obj.isColliding(posX, playerCenterY, posZ, PLAYER_COLLISION_RADIUS, PLAYER_HALF_HEIGHT)) {
                        float objMinY = obj.getPosY() - obj.getScaleY() / 2.0f;
                        float objMaxY = obj.getPosY() + obj.getScaleY() / 2.0f;

                        if (velY <= 0 && prevPlayerCenterY > obj.getPosY()) {
                            playerCenterY = objMaxY + PLAYER_HALF_HEIGHT;
                            velY = 0;
                            onGround = true;
                        }
                        else if (velY > 0 && prevPlayerCenterY < obj.getPosY()) {
                            playerCenterY = objMinY - PLAYER_HALF_HEIGHT;
                            velY = 0;
                        }
                        nextPosY = playerCenterY + PLAYER_HALF_HEIGHT;
                    }

                    // 2. Resolve X
                    if (obj.isColliding(nextPosX, nextPosY - PLAYER_HALF_HEIGHT, posZ, PLAYER_COLLISION_RADIUS, PLAYER_HALF_HEIGHT)) {
                        nextPosX = obj.resolveCollision(nextPosX, prevPosX, obj.getPosX(), obj.getScaleX(), PLAYER_COLLISION_RADIUS);
                    }

                    // 3. Resolve Z
                    if (obj.isColliding(nextPosX, nextPosY - PLAYER_HALF_HEIGHT, nextPosZ, PLAYER_COLLISION_RADIUS, PLAYER_HALF_HEIGHT)) {
                        nextPosZ = obj.resolveCollision(nextPosZ, prevPosZ, obj.getPosZ(), obj.getScaleZ(), PLAYER_COLLISION_RADIUS);
                    }
                }
            }

            // Floor collision
            if (nextPosY < PLAYER_EYE_HEIGHT) {
                nextPosY = PLAYER_EYE_HEIGHT;
                velY = 0;
                onGround = true;
            }

            // Final position update
            posX = nextPosX;
            posY = nextPosY;
            posZ = nextPosZ;
        }
    }

    /**
     * Applies the camera transformations (view matrix) for rendering.
     */
    public void setupCamera() {
        glRotatef(pitch, 1.0f, 0.0f, 0.0f);
        glRotatef(yaw, 0.0f, 1.0f, 0.0f);
        glTranslatef(-posX, -posY, -posZ);
    }

    // --- Getters ---
    public float getPosX() { return posX; }
    public float getPosY() { return posY; }
    public float getPosZ() { return posZ; }
    public float getYaw() { return yaw; }
    public float getPlayerHalfHeight() { return PLAYER_HALF_HEIGHT; }

    /**
     * NEW: Directly sets the player's position, bypassing physics for one frame.
     * Used by the admin panel teleport feature.
     */
    public void teleportTo(float x, float y, float z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.velY = 0; // Stop any falling/jumping momentum
        this.onGround = false; // Force physics re-check next frame
    }
}
package ohio.pugnetgames.chad.game;

import ohio.pugnetgames.chad.game.GameObject.ShapeType;
import ohio.pugnetgames.chad.game.Room.RoomType; // 💥 IMPORT ROOMTYPE 💥

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * Manages the state, logic, and rendering of all keys in the game.
 *
 * MODIFIED: Now spawns keys on pre-defined tables in Bedrooms,
 * and generates tables for Standard/Courtyard rooms.
 */
public class KeyManager {

    private final List<Key> keys = new ArrayList<>();
    private final Random random = new Random();

    private final float KEY_COLLISION_RADIUS_SQ = 1.0f * 1.0f;
    private final float KEY_SCALE = 0.001f;

    private int keysCollected = 0;
    private int TOTAL_KEYS = 3;

    // --- Table constants (for non-bedroom rooms) ---
    private final float TABLE_TOP_Y = 0.8f;
    private final float TABLE_TOP_W = 1.2f; // width (x)
    private final float TABLE_TOP_D = 0.8f; // depth (z)
    private final float TABLE_TOP_H = 0.1f;
    private final float LEG_HEIGHT = 0.8f;
    private final float LEG_SIZE = 0.15f;
    private final float LEG_OFFSET_X = (TABLE_TOP_W / 2.0f) - (LEG_SIZE / 2.0f);
    private final float LEG_OFFSET_Z = (TABLE_TOP_D / 2.0f) - (LEG_SIZE / 2.0f);

    // --- Model and Texture ---
    private Mesh keyModel;
    private int keyTextureID;

    /**
     * Clears all existing keys and spawns a specified number of new ones
     * in random rooms on tables.
     *
     * @param allRooms List of all generated rooms to spawn in.
     * @param staticObjects The world's object list to add tables to.
     * @param totalKeysToSpawn The number of keys to create (e.g., 3 or 10).
     */
    // --- 💥 REWRITTEN: initializeKeys 💥 ---
    public void initializeKeys(List<Room> allRooms, List<GameObject> staticObjects, int totalKeysToSpawn) {
        keys.clear();
        keysCollected = 0;
        this.TOTAL_KEYS = totalKeysToSpawn;

        // --- 1. Load the model and texture ONCE ---
        try {
            this.keyModel = ModelLoader.loadModel("key.obj");
            this.keyTextureID = TextureLoader.loadTexture("key_diffuse.png");
        } catch (Exception e) {
            System.err.println("!!!!!!!!!! FAILED TO LOAD KEY MODEL !!!!!!!!!!");
            e.printStackTrace();
            this.keyModel = null;
        }

        // Create a list of rooms we can spawn in
        List<Room> spawnableRooms = new ArrayList<>(allRooms);
        if (!spawnableRooms.isEmpty()) {
            spawnableRooms.remove(0); // Don't spawn in Start Room
        }
        if (!spawnableRooms.isEmpty()) {
            spawnableRooms.remove(spawnableRooms.size() - 1); // Don't spawn in Escape Room
        }
        Collections.shuffle(spawnableRooms, random);

        // --- 2. Spawn keys in the first X shuffled rooms ---
        for (int i = 0; i < TOTAL_KEYS && i < spawnableRooms.size(); i++) {
            Room room = spawnableRooms.get(i);

            // --- 💥 NEW LOGIC: Check room type 💥 ---
            if (room.getType() == RoomType.BEDROOM) {
                // --- THIS IS A BEDROOM ---
                // Tables are already built. Spawn key on one of the tables.
                List<float[]> spawns = room.keySpawnLocations;
                if (spawns == null || spawns.isEmpty()) {
                    System.err.println("FATAL: Bedroom has no key spawn locations! Spawning at center.");
                    // Fallback: spawn at room center (on the floor)
                    keys.add(new Key(room.getCenterX(), 0.5f, room.getCenterZ()));
                    continue;
                }

                // Pick one of the two tables at random
                float[] chosenSpawn = spawns.get(random.nextInt(spawns.size()));
                Key key = new Key(chosenSpawn[0], chosenSpawn[1], chosenSpawn[2]);
                keys.add(key);
                System.out.println("[KeyManager] Spawning key in BEDROOM at (" + chosenSpawn[0] + ", " + chosenSpawn[2] + ")");

            } else {
                // --- THIS IS A STANDARD/COURTYARD ROOM ---
                // Build a table in the center and spawn the key on it.
                float tableX = room.getCenterX();
                float tableZ = room.getCenterZ();

                // 2a. Create and add the table GameObjects
                staticObjects.add(new GameObject(ShapeType.CUBE, tableX, TABLE_TOP_Y - (TABLE_TOP_H / 2.0f), tableZ,
                        TABLE_TOP_W, TABLE_TOP_H, TABLE_TOP_D, 0.5f, 0.3f, 0.0f));
                staticObjects.add(new GameObject(ShapeType.CUBE, tableX - LEG_OFFSET_X, 0.0f, tableZ - LEG_OFFSET_Z,
                        LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                staticObjects.add(new GameObject(ShapeType.CUBE, tableX + LEG_OFFSET_X, 0.0f, tableZ - LEG_OFFSET_Z,
                        LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                staticObjects.add(new GameObject(ShapeType.CUBE, tableX - LEG_OFFSET_X, 0.0f, tableZ + LEG_OFFSET_Z,
                        LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));
                staticObjects.add(new GameObject(ShapeType.CUBE, tableX + LEG_OFFSET_X, 0.0f, tableZ + LEG_OFFSET_Z,
                        LEG_SIZE, LEG_HEIGHT, LEG_SIZE, 0.5f, 0.3f, 0.0f));

                // 2b. Create the Key on top of the table
                Key key = new Key(tableX, TABLE_TOP_Y + 0.05f, tableZ);
                keys.add(key);
                System.out.println("[KeyManager] Spawning key in STANDARD room at (" + tableX + ", " + tableZ + ")");
            }
            // --- 💥 END NEW LOGIC 💥 ---
        }
    }

    /**
     * Updates all keys. Checks for collection and updates rotation.
     * @param player The player object to check distance against.
     */
    public void update(Player player) {
        for (Key key : keys) {
            // Update rotation
            key.rotation = (System.currentTimeMillis() / 20.0f) % 360; // Slower rotation

            if (key.collected) {
                continue;
            }

            // Handle collection
            float dx = key.x - player.getPosX();
            // 💥 FIX: Use player's body center for Y check 💥
            float dy = key.y - (player.getPosY() - player.getPlayerHalfHeight() + 0.5f);
            float dz = key.z - player.getPosZ();
            float distSq = dx * dx + dy * dy + dz * dz; // 3D distance check

            if (distSq < KEY_COLLISION_RADIUS_SQ) {
                key.collected = true;
                keysCollected++;
            }
        }
    }

    /**
     * Renders all uncollected keys.
     */
    public void render() {
        if (keyModel != null && keyTextureID != 0) {
            // --- Render using the 3D MODEL ---
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, keyTextureID);
            glColor3f(1.0f, 1.0f, 1.0f); // White tint

            for (Key key : keys) {
                if (!key.collected) {
                    renderKey(key);
                }
            }
            glDisable(GL_TEXTURE_2D);

        } else {
            // --- Fallback to rendering YELLOW CUBES ---
            glDisable(GL_TEXTURE_2D);
            glColor3f(1.0f, 1.0f, 0.0f); // Bright Yellow

            for (Key key : keys) {
                if (!key.collected) {
                    glPushMatrix();
                    glTranslatef(key.x, key.y + 0.25f, key.z); // Center the cube
                    glRotatef(key.rotation, 0.0f, 1.0f, 0.0f);
                    drawCube(0.5f, 0.5f, 0.5f);
                    glPopMatrix();
                }
            }
        }
    }

    /**
     * NEW: Helper method to render a single key model.
     * This is used by the main render loop and the admin panel spawner.
     */
    public void renderKey(Key key) {
        if (keyModel == null || keyTextureID == 0) return; // Guard

        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, keyTextureID);
        glColor3f(1.0f, 1.0f, 1.0f);

        glPushMatrix();
        glTranslatef(key.x, key.y, key.z);
        glRotatef(key.rotation, 0.0f, 1.0f, 0.0f);
        glScalef(KEY_SCALE, KEY_SCALE, KEY_SCALE);
        keyModel.render();
        glPopMatrix();

        glDisable(GL_TEXTURE_2D);
    }


    /**
     * @return The total number of keys collected so far.
     */
    public int getKeysCollected() {
        return keysCollected;
    }

    /**
     * @return The total number of keys that exist (dynamic based on difficulty).
     */
    public int getTotalKeys() {
        return TOTAL_KEYS;
    }

    /**
     * Returns the full list of all keys (collected or not).
     * Used by the minimap renderer.
     */
    public List<Key> getKeys() {
        return keys;
    }

    /**
     * Finds the nearest uncollected key (for AI debug line).
     */
    public Key findNearestKey(float pX, float pZ) {
        Key nearest = null;
        float minDstSq = Float.MAX_VALUE;
        for (Key key : keys) {
            if (!key.collected) {
                float dxKey = key.x - pX;
                float dzKey = key.z - pZ;
                float distSq = dxKey * dxKey + dzKey * dzKey;
                if (distSq < minDstSq) {
                    minDstSq = distSq;
                    nearest = key;
                }
            }
        }
        return nearest;
    }

    /**
     * Private utility to draw a simple cube.
     */
    private void drawCube(float sizeX, float sizeY, float sizeZ) {
        float sx2 = sizeX / 2.0f;
        float sy2 = sizeY / 2.0f;
        float sz2 = sizeZ / 2.0f;

        glBegin(GL_QUADS);
        // Front face (+Z)
        glNormal3f(0.0f, 0.0f, 1.0f);
        glVertex3f(-sx2, -sy2, sz2);
        glVertex3f( sx2, -sy2, sz2);
        glVertex3f( sx2,  sy2, sz2);
        glVertex3f(-sx2,  sy2, sz2);
        // Back face (-Z)
        glNormal3f(0.0f, 0.0f, -1.0f);
        glVertex3f(-sx2, -sy2, -sz2);
        glVertex3f(-sx2,  sy2, -sz2);
        glVertex3f( sx2,  sy2, -sz2);
        glVertex3f( sx2, -sy2, -sz2);
        // Top face (+Y)
        glNormal3f(0.0f, 1.0f, 0.0f);
        glVertex3f(-sx2, sy2, -sz2);
        glVertex3f(-sx2, sy2,  sz2);
        glVertex3f( sx2, sy2,  sz2);
        glVertex3f( sx2, sy2, -sz2);
        // Bottom face (-Y)
        glNormal3f(0.0f, -1.0f, 0.0f);
        glVertex3f(-sx2, -sy2, -sz2);
        glVertex3f( sx2, -sy2, -sz2);
        glVertex3f( sx2, -sy2,  sz2);
        glVertex3f(-sx2, -sy2,  sz2);
        // Right face (+X)
        glNormal3f(1.0f, 0.0f, 0.0f);
        glVertex3f( sx2, -sy2, -sz2);
        glVertex3f( sx2,  sy2, -sz2);
        glVertex3f( sx2,  sy2,  sz2);
        glVertex3f( sx2, -sy2,  sz2);
        // Left face (-X)
        glNormal3f(-1.0f, 0.0f, 0.0f);
        glVertex3f(-sx2, -sy2, -sz2);
        glVertex3f(-sx2, -sy2,  sz2);
        glVertex3f(-sx2,  sy2,  sz2);
        glVertex3f(-sx2,  sy2, -sz2);
        glEnd();
    }
}
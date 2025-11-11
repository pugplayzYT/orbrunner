package ohio.pugnetgames.chad.game;

import java.util.List;

/**
 * A container class to hold all world-related data,
 * including static geometry, room data, and escape objects.
 * MODIFIED: Removed orb zones, added rooms and escape objects.
 */
public class World {

    private final List<GameObject> staticObjects;

    // NEW: Textures passed from GamePanel
    private final int wallTextureID;
    private final int orbTextureID;

    // MODIFIED: Replaced orbSpawnZones with allRooms
    private final List<Room> allRooms;

    // --- NEW: Escape objects ---
    private final GameObject escapeDoor;
    private final GameObject winTrigger;

    public World(List<GameObject> staticObjects, List<Room> allRooms,
                 GameObject escapeDoor, GameObject winTrigger,
                 int wallTextureID, int orbTextureID) {
        this.staticObjects = staticObjects;
        this.allRooms = allRooms;
        this.escapeDoor = escapeDoor;
        this.winTrigger = winTrigger;
        this.wallTextureID = wallTextureID;
        this.orbTextureID = orbTextureID;
    }

    public List<GameObject> getStaticObjects() {
        return staticObjects;
    }

    // --- MODIFIED: Getters for new fields ---
    public List<Room> getAllRooms() {
        return allRooms;
    }

    public GameObject getEscapeDoor() {
        return escapeDoor;
    }

    public GameObject getWinTrigger() {
        return winTrigger;
    }
    // --- END MODIFIED ---

    // NEW: Getters for textures
    public int getWallTextureID() {
        return wallTextureID;
    }

    public int getOrbTextureID() {
        return orbTextureID;
    }
}
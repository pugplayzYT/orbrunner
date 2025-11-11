package ohio.pugnetgames.chad.game;

import ohio.pugnetgames.chad.core.BuildManager;
import ohio.pugnetgames.chad.game.GameObject.ShapeType;
import ohio.pugnetgames.chad.game.Room.RoomType;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * WorldLoader now procedurally generates a random map of rooms and tunnels
 * every time the game starts.
 * MODIFIED: It now also generates a special "Escape Hallway" with a door,
 * and win trigger. It no longer generates orb spawn zones.
 */
public class WorldLoader {

    // --- Generation Constants ---
    private static final float WALL_HEIGHT = 8.0f;
    private static final float WALL_THICKNESS = 0.1f;
    private static final float TUNNEL_WIDTH = 4.0f;
    private static final float TUNNEL_HEIGHT = 3.0f;

    private static final float MIN_ROOM_SIZE = 15.0f;
    private static final float MAX_ROOM_SIZE = 30.0f;
    private static final float MIN_TUNNEL_LENGTH = 8.0f;
    private static final float MAX_TUNNEL_LENGTH = 15.0f;

    // --- NEW ESCAPE CONSTANTS ---
    private static final float HALLWAY_LENGTH = 20.0f;
    private static final float WIN_TRIGGER_SIZE = 1.0f; // This is now the *thickness*

    // --- NEW TREE CONSTANTS ---
    private static final int TREES_PER_COURTYARD = 8;
    private static final float TRUNK_HEIGHT = 4.0f;
    private static final float TRUNK_WIDTH = 0.4f;
    private static final float LEAF_RADIUS = 2.0f;
    private static final float TREE_INSET = 2.5f; // Distance from walls/orb zone edges

    // --- Generator State ---
    private Random random;
    private List<GameObject> staticObjects;
    // MODIFIED: Removed orbSpawnZones
    private List<Room> allRooms;

    // NEW FIELD: Set to true if feature.allcourtyards.enabled is true
    private boolean forceCourtyards = false;

    // --- NEW: Escape object references ---
    private GameObject escapeDoor;
    private GameObject winTrigger;
    // FIX: Removed private Room escapeRoom;

    // MODIFIED: Wall texture is provided, but we need Orb texture too
    private int wallTextureID;
    private int orbTextureID; // NEW: The grass texture from the orb

    /**
     * Creates and returns a World object containing all static GameObjects and room data.
     * @param wallTextureID The texture ID for walls.
     * @param orbTextureID The texture ID for orbs (used as grass floor).
     * @return A new, randomly generated World object.
     */
    public World generateWorld(int wallTextureID, int orbTextureID) {
        // 1. Init state
        this.random = new Random();
        this.staticObjects = new ArrayList<>();
        this.allRooms = new ArrayList<>();
        this.wallTextureID = wallTextureID;
        this.orbTextureID = orbTextureID;

        // --- NEW: Reset escape objects ---
        this.escapeDoor = null;
        this.winTrigger = null;
        // FIX: Removed this.escapeRoom = null;

        // NEW: Load feature flag
        this.forceCourtyards = BuildManager.getBoolean("feature.allcourtyards.enabled");

        // 2. Define how many rooms to make (50 or 51)
        // FIX: totalRoomsToGenerate is the number of actual rooms. The Escape Tunnel
        // is the last connection, which doesn't count as a room.
        int totalRoomsToGenerate = random.nextInt(2) + 50; // 50 or 51

        List<Room> roomsToProcess = new ArrayList<>();

        // 3. Create the first (start) room
        float startSizeX = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);
        float startSizeZ = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);

        // FIX: Ensure start room respects the forceCourtyards flag
        RoomType startRoomType = this.forceCourtyards ? RoomType.COURTYARD : RoomType.STANDARD;
        Room startRoom = new Room(
                -startSizeX / 2.0f, -startSizeZ / 2.0f,
                startSizeX / 2.0f,  startSizeZ / 2.0f,
                startRoomType
        );
        allRooms.add(startRoom);
        roomsToProcess.add(startRoom);
        log("generateWorld", "Created Start Room (" + startRoomType + ") at (0, 0) with size (" + startSizeX + ", " + startSizeZ + ")");

        // --- NEW: DEBUG PRINT + LOGIC FIX ---
        log("generateWorld", "Starting generation. Goal: " + totalRoomsToGenerate + " rooms + 1 Escape Tunnel.");
        boolean escapeTunnelBuilt = false; // REAL FIX: Flag to stop the WHILE loop

        // 4. Main generation loop
        // REAL FIX: Added !escapeTunnelBuilt to the loop condition
        while (allRooms.size() < totalRoomsToGenerate && !roomsToProcess.isEmpty() && !escapeTunnelBuilt) {

            Room currentRoom = roomsToProcess.get(random.nextInt(roomsToProcess.size()));
            List<Direction> availableWalls = currentRoom.getAvailableWalls();

            if (availableWalls.isEmpty()) {
                roomsToProcess.remove(currentRoom);
                continue;
            }

            // Try up to 4 times to find a free direction
            boolean success = false;
            for (int attempt = 0; attempt < 4 && !availableWalls.isEmpty(); attempt++) {
                Direction buildDirection = availableWalls.get(random.nextInt(availableWalls.size()));

                // --- NEW: Decide if this is the escape room or a courtyard ---
                // The last room to be generated will be the escape tunnel/hallway.
                boolean isEscapeTunnel = allRooms.size() == totalRoomsToGenerate - 1;

                // Original courtyard logic (1/5 chance, not escape room)
                boolean defaultCourtyard = !isEscapeTunnel && random.nextInt(5) == 0;

                // NEW LOGIC: Override default if the build feature is enabled
                boolean isCourtyard = this.forceCourtyards ? !isEscapeTunnel : defaultCourtyard;
                // --- END NEW ---

                // FIX: newRoom is either a new Room object or null if it's the Escape Tunnel
                Room newRoom = buildTunnelAndNextRoom(currentRoom, buildDirection, isCourtyard, isEscapeTunnel);

                // If collision check passed (newRoom is not null) OR it was the successful Escape Tunnel build (which returns null)
                if (newRoom != null || (newRoom == null && isEscapeTunnel)) {
                    if (newRoom != null) { // Only add if it's a real room
                        allRooms.add(newRoom);
                        roomsToProcess.add(newRoom);
                        log("generateWorld", "Successfully added new " + newRoom.getType() + " room. Total rooms: " + allRooms.size());
                    } else if (isEscapeTunnel) {
                        // FIX: Remove the current room from the process list as it is now connected
                        // to the final, dead-end escape tunnel and should not generate further rooms.
                        roomsToProcess.remove(currentRoom);

                        // --- REAL FIX HERE ---
                        // Set the flag to true so the main WHILE loop stops
                        escapeTunnelBuilt = true;
                        // --- END REAL FIX ---
                    }
                    success = true;
                    break; // This break is correct, it's for the inner 'for' loop
                } else {
                    // Collision detected, mark wall as used so we don't try that direction again
                    // The markWallUsed is handled inside buildTunnelAndNextRoom upon failure
                    availableWalls.remove(buildDirection);
                }
            }

            // If all directions failed, remove the current room and move on
            if (!success) {
                roomsToProcess.remove(currentRoom);
            }
        }

        // --- NEW: DEBUG PRINT ---
        if (escapeTunnelBuilt) {
            log("generateWorld", "Generation complete. Escape tunnel was built.");
        } else {
            log("generateWorld", "Generation FAILED. WARNING: Escape tunnel was NOT built.");
        }

        // 5. Build all room geometry (the escape tunnel is already built in the main loop)
        for (Room room : allRooms) {
            // FIX: Removed if (room == escapeRoom) check
            buildRoomFloorAndRoof(room);
            buildRoomWalls(room);
        }

        // MODIFIED: Return new World object with updated signature
        log("generateWorld", "Returning new World with " + staticObjects.size() + " static objects.");
        return new World(staticObjects, allRooms, escapeDoor, winTrigger, wallTextureID, orbTextureID);
    }

    /**
     * --- Helper to build just the floor and roof for a room ---
     */
    private void buildRoomFloorAndRoof(Room room) {
        int floorTexture = room.getType() == RoomType.COURTYARD ? orbTextureID : wallTextureID;
        String roomType = room.getType().toString();

        // Add Floor
        GameObject floor = new GameObject(ShapeType.PLANE, room.getCenterX(), 0.0f, room.getCenterZ(), room.getWidth(), 0.0f, room.getDepth(), floorTexture);
        staticObjects.add(floor);
        log("buildRoomFloorAndRoof", "Added " + roomType + " Floor at " + pos(floor));

        // Only add a roof to STANDARD rooms (courtyards are open to the sky/void)
        if (room.getType() == RoomType.STANDARD) {
            // FIX: Changed ShapeType from PLANE to CUBE and gave it a WALL_THICKNESS (0.1f) height.
            // This ensures the object is detected by the player's CUBE-only collision logic.
            GameObject roof = new GameObject(ShapeType.CUBE, room.getCenterX(), WALL_HEIGHT, room.getCenterZ(), room.getWidth(), WALL_THICKNESS, room.getDepth(), wallTextureID);
            staticObjects.add(roof);
            log("buildRoomFloorAndRoof", "Added " + roomType + " Roof at " + pos(roof));
        }

        // MODIFIED: Removed orb spawn zone logic

        // NEW: Generate trees in courtyards
        if (room.getType() == RoomType.COURTYARD) {
            generateTreesForRoom(room);
        }
    }

    /**
     * NEW: Generates trees randomly within a Courtyard room, avoiding walls and existing trees.
     */
    private void generateTreesForRoom(Room room) {
        log("generateTreesForRoom", "Generating " + TREES_PER_COURTYARD + " trees for Courtyard at " + room.getCenterX() + ", " + room.getCenterZ());
        for (int i = 0; i < TREES_PER_COURTYARD; i++) {

            // Define a restricted area for tree placement (inwards from walls)
            float treeMinX = room.minX + TREE_INSET;
            float treeMaxX = room.maxX - TREE_INSET;
            float treeMinZ = room.minZ + TREE_INSET;
            float treeMaxZ = room.maxZ - TREE_INSET;

            if (treeMinX >= treeMaxX || treeMinZ >= treeMaxZ) {
                // Room is too small for trees, skip
                log("generateTreesForRoom", "Room is too small, skipping remaining trees.");
                break;
            }

            float treeX = randRange(treeMinX, treeMaxX);
            float treeZ = randRange(treeMinZ, treeMaxZ);

            // Simple collision check against existing objects (mainly other trees)
            boolean safeToPlace = true;
            for (GameObject existingObj : staticObjects) {
                // For simplicity and performance, only check against other potential trees (using the trunk height as a marker)
                if (existingObj.getShape() == ShapeType.CUBE && existingObj.getScaleY() == TRUNK_HEIGHT) {
                    float dx = existingObj.getPosX() - treeX;
                    float dz = existingObj.getPosZ() - treeZ;
                    // Check if centers are closer than the radius of the leaves + buffer
                    if (dx * dx + dz * dz < (LEAF_RADIUS * 2) * (LEAF_RADIUS * 2)) {
                        safeToPlace = false;
                        break;
                    }
                }
            }

            if (safeToPlace) {
                // 1. Create the TRUNK (CUBE: Collidable, Renderable, Brown)
                GameObject trunk = new GameObject(
                        ShapeType.CUBE,
                        treeX, 0.0f, treeZ,
                        TRUNK_WIDTH, TRUNK_HEIGHT, TRUNK_WIDTH,
                        0.5f, 0.3f, 0.0f, // Brown color
                        true, true // Collidable=true, Renderable=true
                );

                // 2. Create the LEAVES (SPHERE: Non-collidable, Renderable, Green)
                float leafY = TRUNK_HEIGHT;
                GameObject leaves = new GameObject(
                        ShapeType.SPHERE,
                        treeX, leafY, treeZ,
                        LEAF_RADIUS, LEAF_RADIUS, LEAF_RADIUS,
                        0.0f, 0.7f, 0.0f, // Green color
                        false, true // Collidable=false, Renderable=true
                );

                // Add to static objects
                staticObjects.add(trunk);
                staticObjects.add(leaves);
                log("generateTreesForRoom", "Added Tree (Trunk at " + pos(trunk) + ", Leaves at " + pos(leaves) + ")");
            } else {
                log("generateTreesForRoom", "Skipped tree placement at (" + treeX + ", " + treeZ + ") due to collision.");
            }
        }
    }

    /**
     * --- Helper to build all 4 walls for a room, checking for holes ---
     */
    private void buildRoomWalls(Room room) {
        String roomType = room.getType().toString();
        log("buildRoomWalls", "Building " + roomType + " walls for room at " + room.getCenterX() + ", " + room.getCenterZ());

        // Wall properties change based on room type
        int wallTex = room.getType() == RoomType.COURTYARD ? 0 : wallTextureID; // 0 for invisible
        float wallHeight = room.getType() == RoomType.COURTYARD ? 100.0f : WALL_HEIGHT; // Very tall to see the void
        boolean collidable = true; // All walls are collidable to contain the player
        boolean renderable = room.getType() != RoomType.COURTYARD; // Only render if it's NOT a courtyard

        // North Wall (+Z)
        if (room.northWallUsed) {
            buildWallWithHole(room.minX, room.maxX, room.maxZ, room.maxZ, room.getCenterX(), wallTex, collidable, renderable);
        } else {
            buildWall(room.minX, room.maxX, room.maxZ, room.maxZ, false, wallTex, collidable, renderable, wallHeight);
        }

        // South Wall (-Z)
        if (room.southWallUsed) {
            buildWallWithHole(room.minX, room.maxX, room.minZ, room.minZ, room.getCenterX(), wallTex, collidable, renderable);
        } else {
            buildWall(room.minX, room.maxX, room.minZ, room.minZ, false, wallTex, collidable, renderable, wallHeight);
        }

        // East Wall (+X)
        if (room.eastWallUsed) {
            buildWallWithHole(room.maxX, room.maxX, room.minZ, room.maxZ, room.getCenterZ(), wallTex, collidable, renderable);
        } else {
            buildWall(room.maxX, room.maxX, room.minZ, room.maxZ, true, wallTex, collidable, renderable, wallHeight);
        }

        // West Wall (-X)
        if (room.westWallUsed) {
            buildWallWithHole(room.minX, room.minX, room.minZ, room.maxZ, room.getCenterZ(), wallTex, collidable, renderable);
        } else {
            buildWall(room.minX, room.minX, room.minZ, room.maxZ, true, wallTex, collidable, renderable, wallHeight);
        }
    }

    /**
     * Generation function. Creates a tunnel and a new room object.
     * @param isCourtyard If true, the new room will be a Courtyard.
     * @param isEscapeTunnel If true, the new room will be the Escape Tunnel.
     * @return The new Room object if generation was successful and no collision was found, otherwise null.
     */
    // MODIFIED: Parameter name changed to reflect new intent
    private Room buildTunnelAndNextRoom(Room fromRoom, Direction direction, boolean isCourtyard, boolean isEscapeTunnel) {
        // 1. Mark the wall on the "from" room as used (Temporary mark until success is confirmed)
        fromRoom.markWallUsed(direction);

        // 2. Define tunnel and new room
        float tunnelLength = randRange(MIN_TUNNEL_LENGTH, MAX_TUNNEL_LENGTH);
        float newRoomWidth = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);
        float newRoomDepth = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);

        float newRoomMinX=0, newRoomMinZ=0, newRoomMaxX=0, newRoomMaxZ=0;
        float tunnelMinX=0, tunnelMinZ=0, tunnelMaxX=0, tunnelMaxZ=0;

        // 3. Calculate all positions based on direction
        switch (direction) {
            case NORTH:
                tunnelMinX = fromRoom.getCenterX() - TUNNEL_WIDTH / 2.0f;
                tunnelMaxX = fromRoom.getCenterX() + TUNNEL_WIDTH / 2.0f;
                tunnelMinZ = fromRoom.maxZ;
                tunnelMaxZ = fromRoom.maxZ + tunnelLength;

                newRoomMinX = fromRoom.getCenterX() - newRoomWidth / 2.0f;
                newRoomMaxX = fromRoom.getCenterX() + newRoomWidth / 2.0f;
                newRoomMinZ = tunnelMaxZ;
                newRoomMaxZ = tunnelMaxZ + newRoomDepth;
                break;
            case SOUTH:
                tunnelMinX = fromRoom.getCenterX() - TUNNEL_WIDTH / 2.0f;
                tunnelMaxX = fromRoom.getCenterX() + TUNNEL_WIDTH / 2.0f;
                tunnelMaxZ = fromRoom.minZ;
                tunnelMinZ = fromRoom.minZ - tunnelLength;

                newRoomMinX = fromRoom.getCenterX() - newRoomWidth / 2.0f;
                newRoomMaxX = fromRoom.getCenterX() + newRoomWidth / 2.0f;
                newRoomMaxZ = tunnelMinZ;
                newRoomMinZ = tunnelMinZ - newRoomDepth;
                break;
            case EAST:
                tunnelMinZ = fromRoom.getCenterZ() - TUNNEL_WIDTH / 2.0f;
                tunnelMaxZ = fromRoom.getCenterZ() + TUNNEL_WIDTH / 2.0f;
                tunnelMinX = fromRoom.maxX;
                tunnelMaxX = fromRoom.maxX + tunnelLength;

                newRoomMinZ = fromRoom.getCenterZ() - newRoomDepth / 2.0f;
                newRoomMaxZ = fromRoom.getCenterZ() + newRoomDepth / 2.0f;
                newRoomMinX = tunnelMaxX;
                newRoomMaxX = tunnelMaxX + newRoomWidth;
                break;
            case WEST:
                tunnelMinZ = fromRoom.getCenterZ() - TUNNEL_WIDTH / 2.0f;
                tunnelMaxZ = fromRoom.getCenterZ() + TUNNEL_WIDTH / 2.0f;
                tunnelMaxX = fromRoom.minX;
                tunnelMinX = fromRoom.minX - tunnelLength;

                newRoomMinZ = fromRoom.getCenterZ() - newRoomDepth / 2.0f;
                newRoomMaxZ = fromRoom.getCenterZ() + newRoomDepth / 2.0f;
                newRoomMaxX = tunnelMinX;
                newRoomMinX = tunnelMinX - newRoomWidth;
                break;
        }

        // --- NEW COLLISION FIX ---
        // Create a "checkBounds" room object to test for collision.
        // This *one* object will be either the new room OR the new tunnel.

        Room checkBounds;
        // The room type is passed in correctly from generateWorld after the feature flag logic.
        RoomType type = isCourtyard ? RoomType.COURTYARD : RoomType.STANDARD;

        if (isEscapeTunnel) {
            // If it's the escape tunnel, the bounds are just the tunnel itself
            log("buildTunnelAndNextRoom", "Checking bounds for ESCAPE TUNNEL at " + tunnelMinX + ", " + tunnelMinZ);
            checkBounds = new Room(tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);
        } else {
            // Otherwise, the bounds are the new room
            log("buildTunnelAndNextRoom", "Checking bounds for new " + type + " room at " + newRoomMinX + ", " + newRoomMinZ);
            checkBounds = new Room(newRoomMinX, newRoomMinZ, newRoomMaxX, newRoomMaxZ, type);
        }

        // Check for collision with all existing rooms (excluding the one we are connecting from)
        // Use a padding of 1.0f to ensure a small gap between rooms
        for (Room existingRoom : allRooms) {
            if (existingRoom == fromRoom) continue; // Skip checking against the source room

            if (checkBounds.overlaps(existingRoom, 1.0f)) {
                // FIX: Collision detected! Revert the wall mark on the fromRoom.
                log("buildTunnelAndNextRoom", "COLLISION DETECTED. Failed to build " + direction + " from room at " + fromRoom.getCenterX() + ", " + fromRoom.getCenterZ());
                fromRoom.unmarkWallUsed(direction);
                return null;
            }
        }
        // --- END COLLISION FIX ---


        // --- If no collision, proceed to build ---

        // Handle Escape Tunnel case
        if (isEscapeTunnel) {
            // FIX: Don't check for room collision, just build the special dead-end tunnel
            buildDeadEndEscapeTunnel(fromRoom, direction, tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);
            // Return null to signal success and stop the room generation loop, but don't add a room.
            return null;
        }

        // Handle Standard/Courtyard Room case
        // 'checkBounds' is our newRoom, so we just mark its wall and build the tunnel
        checkBounds.markWallUsed(direction.getOpposite());
        buildTunnelObjects(tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);

        // Return the new room ('checkBounds') so it can be added to allRooms list
        return checkBounds;
    }

    /**
     * --- NEW: Builds the Dead-End Escape Hallway with Door and Win Trigger ---
     */
    private void buildDeadEndEscapeTunnel(Room fromRoom, Direction direction, float tunnelMinX, float tunnelMinZ, float tunnelMaxX, float tunnelMaxZ) {
        // --- NEW: DEBUG PRINT ---
        log("buildDeadEndEscapeTunnel", "--- GENERATING ESCAPE TUNNEL ---");
        log("buildDeadEndEscapeTunnel", "Building exit from Room at: (" + fromRoom.getCenterX() + ", " + fromRoom.getCenterZ() + ") facing " + direction);

        float centerX = (tunnelMinX + tunnelMaxX) / 2.0f;
        float centerZ = (tunnelMinZ + tunnelMaxZ) / 2.0f;
        float width = tunnelMaxX - tunnelMinX;
        // --- COMPILER FIX (from last time, still needed) ---
        float depth = tunnelMaxZ - tunnelMinZ;
        // --- END COMPILER FIX ---

        // 1. Build the connecting tunnel geometry
        buildTunnelObjects(tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);

        // 2. Define positions for Door, Hallway (Hallway is the connecting tunnel), Win Trigger, and End Wall
        float doorX = 0, doorZ = 0, doorW = 0, doorH = TUNNEL_HEIGHT, doorD = 0;
        float endWallX = 0, endWallZ = 0, endWallW = 0, endWallD = 0;
        float triggerX = 0, triggerZ = 0, triggerW = 0, triggerD = 0;

        float triggerHalfSize = WIN_TRIGGER_SIZE / 2.0f;
        float wallHalfSize = WALL_THICKNESS / 2.0f;

        // Calculate positions based on direction (the tunnel extends from the room)
        switch (direction) {
            case NORTH: // Tunnel extends in +Z
                // Door at the base, next to fromRoom's maxZ wall
                doorX = fromRoom.getCenterX(); doorZ = fromRoom.maxZ + (WALL_THICKNESS / 2.0f);
                doorW = TUNNEL_WIDTH; doorD = WALL_THICKNESS;
                // End Wall at maxZ of the tunnel
                endWallX = centerX; endWallZ = tunnelMaxZ - wallHalfSize;
                endWallW = width; endWallD = WALL_THICKNESS;
                // Trigger just in front of the End Wall (-Z)
                triggerX = centerX; triggerZ = endWallZ - wallHalfSize - triggerHalfSize;
                triggerW = width; triggerD = WIN_TRIGGER_SIZE;
                break;
            case SOUTH: // Tunnel extends in -Z
                // Door at the base, next to fromRoom's minZ wall
                doorX = fromRoom.getCenterX(); doorZ = fromRoom.minZ - (WALL_THICKNESS / 2.0f);
                doorW = TUNNEL_WIDTH; doorD = WALL_THICKNESS;
                // End Wall at minZ of the tunnel
                endWallX = centerX; endWallZ = tunnelMinZ + wallHalfSize;
                endWallW = width; endWallD = WALL_THICKNESS;
                // Trigger just in front of the End Wall (+Z)
                triggerX = centerX; triggerZ = endWallZ + wallHalfSize + triggerHalfSize;
                triggerW = width; triggerD = WIN_TRIGGER_SIZE;
                break;
            case EAST: // Tunnel extends in +X
                // Door at the base, next to fromRoom's maxX wall
                doorX = fromRoom.maxX + (WALL_THICKNESS / 2.0f); doorZ = fromRoom.getCenterZ();
                doorW = WALL_THICKNESS; doorD = TUNNEL_WIDTH;
                // End Wall at maxX of the tunnel
                endWallX = tunnelMaxX - wallHalfSize; endWallZ = centerZ;
                endWallW = WALL_THICKNESS; endWallD = depth;
                // Trigger just in front of the End Wall (-X)
                triggerX = endWallX - wallHalfSize - triggerHalfSize; triggerZ = centerZ;
                triggerW = WIN_TRIGGER_SIZE; triggerD = depth;
                break;
            case WEST: // Tunnel extends in -X
                // Door at the base, next to fromRoom's minX wall
                doorX = fromRoom.minX - (WALL_THICKNESS / 2.0f); doorZ = fromRoom.getCenterZ();
                doorW = WALL_THICKNESS; doorD = TUNNEL_WIDTH;
                // End Wall at minX of the tunnel
                endWallX = tunnelMinX + wallHalfSize; endWallZ = centerZ;
                endWallW = WALL_THICKNESS; endWallD = depth;
                // Trigger just in front of the End Wall (+X)
                triggerX = endWallX + wallHalfSize + triggerHalfSize; triggerZ = centerZ;
                triggerW = WIN_TRIGGER_SIZE; triggerD = depth;
                break;
        }

        // 3. Create Door (Brown, Collidable)
        escapeDoor = new GameObject(ShapeType.CUBE, doorX, 0.0f, doorZ, doorW, doorH, doorD, 0.5f, 0.3f, 0.0f, true, true);
        staticObjects.add(escapeDoor);
        log("buildDeadEndEscapeTunnel", "Added ESCAPE DOOR at " + pos(escapeDoor));


        // 4. Create End Wall (Solid, Collidable)
        GameObject endWall = new GameObject(
                ShapeType.CUBE,
                endWallX, 0.0f, endWallZ,
                endWallW, TUNNEL_HEIGHT, endWallD, // Full height of the tunnel
                wallTextureID
        );
        staticObjects.add(endWall);
        log("buildDeadEndEscapeTunnel", "Added End Wall at " + pos(endWall));

        // 5. Create Win Trigger (White, Non-Collidable)
        winTrigger = new GameObject(ShapeType.CUBE, triggerX, 0.0f, triggerZ,
                triggerW, TUNNEL_HEIGHT, triggerD,
                1.0f, 1.0f, 1.0f, // White
                false, true); // Not collidable
        staticObjects.add(winTrigger);
        log("buildDeadEndEscapeTunnel", "Added WIN TRIGGER at " + pos(winTrigger));
    }


    /**
     * Generates the 4 GameObjects for a tunnel (floor, roof, 2 side walls).
     */
    private void buildTunnelObjects(float minX, float minZ, float maxX, float maxZ) {
        log("buildTunnelObjects", "Building tunnel between (" + minX + ", " + minZ + ") and (" + maxX + ", " + maxZ + ")");
        float centerX = (minX + maxX) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;
        float width = maxX - minX;
        float depth = maxZ - minZ;

        // Floor
        GameObject floor = new GameObject(ShapeType.CUBE, centerX, 0.0f, centerZ, width, 0.1f, depth, wallTextureID);
        staticObjects.add(floor);
        log("buildTunnelObjects", "Added Tunnel Floor at " + pos(floor));
        // Roof
        GameObject roof = new GameObject(ShapeType.CUBE, centerX, TUNNEL_HEIGHT, centerZ, width, 0.1f, depth, wallTextureID);
        staticObjects.add(roof);
        log("buildTunnelObjects", "Added Tunnel Roof at " + pos(roof));


        if (width > depth) { // Tunnel is EAST-WEST (Length on X-axis)
            // North wall of tunnel (at maxZ)
            GameObject wallN = new GameObject(ShapeType.CUBE, centerX, 0.0f, maxZ - (WALL_THICKNESS / 2.0f), width, TUNNEL_HEIGHT, WALL_THICKNESS, wallTextureID);
            staticObjects.add(wallN);
            log("buildTunnelObjects", "Added Tunnel N Wall at " + pos(wallN));
            // South wall of tunnel (at minZ)
            GameObject wallS = new GameObject(ShapeType.CUBE, centerX, 0.0f, minZ + (WALL_THICKNESS / 2.0f), width, TUNNEL_HEIGHT, WALL_THICKNESS, wallTextureID);
            staticObjects.add(wallS);
            log("buildTunnelObjects", "Added Tunnel S Wall at " + pos(wallS));
        } else { // Tunnel is NORTH-SOUTH (Length on Z-axis)
            // East wall of tunnel (at maxX)
            GameObject wallE = new GameObject(ShapeType.CUBE, maxX - (WALL_THICKNESS / 2.0f), 0.0f, centerZ, WALL_THICKNESS, TUNNEL_HEIGHT, depth, wallTextureID);
            staticObjects.add(wallE);
            log("buildTunnelObjects", "Added Tunnel E Wall at " + pos(wallE));
            // West wall of tunnel (at minX)
            GameObject wallW = new GameObject(ShapeType.CUBE, minX + (WALL_THICKNESS / 2.0f), 0.0f, centerZ, WALL_THICKNESS, TUNNEL_HEIGHT, depth, wallTextureID);
            staticObjects.add(wallW);
            log("buildTunnelObjects", "Added Tunnel W Wall at " + pos(wallW));
        }

        // MODIFIED: Removed orb spawn zone
    }

    /** * Helper: Builds a single, solid wall GameObject.
     * @param textureId The OpenGL texture ID (0 for no texture / invisible).
     * @param collidable Whether the player should collide with this object.
     * @param renderable Whether the object should be rendered.
     * @param height The height of the wall.
     */
    private void buildWall(float minX, float maxX, float minZ, float maxZ, boolean isVertical, int textureId, boolean collidable, boolean renderable, float height) {
        float centerX = (minX + maxX) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;
        float width = isVertical ? WALL_THICKNESS : (maxX - minX);
        float depth = isVertical ? (maxZ - minZ) : WALL_THICKNESS;

        GameObject wall = new GameObject(ShapeType.CUBE, centerX, 0.0f, centerZ, width, height, depth, textureId, collidable, renderable);
        staticObjects.add(wall);
        log("buildWall", "Added Solid Wall at " + pos(wall));
    }

    /** * Helper: Builds a wall with a hole (3 GameObjects).
     * @param textureId The OpenGL texture ID (0 for no texture / invisible).
     * @param collidable Whether the player should collide with this object.
     * @param renderable Whether the object should be rendered.
     */
    private void buildWallWithHole(float minX, float maxX, float minZ, float maxZ, float holeCenter, int textureId, boolean collidable, boolean renderable) {
        float halfGap = TUNNEL_WIDTH / 2.0f;
        float lintelY = TUNNEL_HEIGHT;
        float lintelHeight = WALL_HEIGHT - TUNNEL_HEIGHT;
        float finalWallHeight = collidable ? WALL_HEIGHT : 100.0f; // Use tall height for void walls

        boolean isVertical = (minX == maxX);

        if (isVertical) {
            // Wall is on X-plane (East/West)
            float segmentLength1 = (holeCenter - halfGap) - minZ;
            float segmentLength2 = maxZ - (holeCenter + halfGap);
            float wallX = minX;

            // Segment 1 (Bottom)
            GameObject seg1 = new GameObject(ShapeType.CUBE, wallX, 0.0f, minZ + segmentLength1 / 2.0f, WALL_THICKNESS, finalWallHeight, segmentLength1, textureId, collidable, renderable);
            staticObjects.add(seg1);
            // Segment 2 (Top)
            GameObject seg2 = new GameObject(ShapeType.CUBE, wallX, 0.0f, maxZ - segmentLength2 / 2.0f, WALL_THICKNESS, finalWallHeight, segmentLength2, textureId, collidable, renderable);
            staticObjects.add(seg2);
            // Lintel (Header)
            GameObject lintel = new GameObject(ShapeType.CUBE, wallX, lintelY, holeCenter, WALL_THICKNESS, lintelHeight, TUNNEL_WIDTH, textureId, collidable, renderable);
            staticObjects.add(lintel);
            log("buildWallWithHole", "Added Vertical Holed Wall (3 parts) at X=" + wallX);

        } else {
            // Wall is on Z-plane (North/South)
            float segmentLength1 = (holeCenter - halfGap) - minX;
            float segmentLength2 = maxX - (holeCenter + halfGap);
            float wallZ = minZ;

            // Segment 1 (Left)
            GameObject seg1 = new GameObject(ShapeType.CUBE, minX + segmentLength1 / 2.0f, 0.0f, wallZ, segmentLength1, finalWallHeight, WALL_THICKNESS, textureId, collidable, renderable);
            staticObjects.add(seg1);
            // Segment 2 (Right)
            GameObject seg2 = new GameObject(ShapeType.CUBE, maxX - segmentLength2 / 2.0f, 0.0f, wallZ, segmentLength2, finalWallHeight, WALL_THICKNESS, textureId, collidable, renderable);
            staticObjects.add(seg2);
            // Lintel (Header)
            GameObject lintel = new GameObject(ShapeType.CUBE, holeCenter, lintelY, wallZ, TUNNEL_WIDTH, lintelHeight, WALL_THICKNESS, textureId, collidable, renderable);
            staticObjects.add(lintel);
            log("buildWallWithHole", "Added Horizontal Holed Wall (3 parts) at Z=" + wallZ);
        }
    }

    /**
     * @return A random float between min (inclusive) and max (exclusive).
     */
    private float randRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    // --- NEW LOGGING HELPERS ---

    /**
     * Helper to format a GameObject's position for logging.
     * @param obj The GameObject.
     * @return A formatted (x, y, z) string.
     */
    private String pos(GameObject obj) {
        return String.format("(%.2f, %.2f, %.2f)", obj.getPosX(), obj.getPosY(), obj.getPosZ());
    }

    /**
     * Custom log function to prefix messages.
     * @param method The method a log is coming from.
     * @param message The log message.
     */
    private void log(String method, String message) {
        System.out.println("[WorldLoader - " + method + "] " + message);
    }
}
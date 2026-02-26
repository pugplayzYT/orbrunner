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
 *
 * MODIFIED: Now generates Bedrooms with 2 beds, 2 tables, and saves
 * key spawn locations to the Room object.
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

    // --- 💥 BEDROOM FURNITURE CONSTANTS 💥 ---
    private static final float BED_WIDTH = 2.5f;  // X-axis (Increased depth into the room)
    private static final float BED_LENGTH = 3.0f; // Z-axis (Increased width along the wall)
    private static final float BED_HEIGHT = 0.4f; // Y-axis (mattress)
    private static final float BED_LEG_HEIGHT = 0.35f;
    private static final float BED_LEG_SIZE = 0.15f;
    private static final float HEADBOARD_HEIGHT = 1.2f;
    private static final float HEADBOARD_THICKNESS = 0.1f;

    private static final float TABLE_TOP_W = 1.2f; // width (x)
    private static final float TABLE_TOP_D = 0.8f; // depth (z)
    private static final float TABLE_TOP_H = 0.1f;
    private static final float TABLE_TOP_Y = 0.8f; // Height of table surface
    private static final float TABLE_LEG_HEIGHT = 0.8f;
    private static final float TABLE_LEG_SIZE = 0.15f;
    private static final float TABLE_LEG_OFFSET_X = (TABLE_TOP_W / 2.0f) - (TABLE_LEG_SIZE / 2.0f);
    private static final float TABLE_LEG_OFFSET_Z = (TABLE_TOP_D / 2.0f) - (TABLE_LEG_SIZE / 2.0f);

    private static final float FURNITURE_WALL_PADDING = 0.5f; // Distance from wall
    // --- 💥 END CONSTANTS 💥 ---


    // --- Generator State ---
    private Random random;
    private long lastSeed;
    private List<GameObject> staticObjects;
    private List<Room> allRooms;
    private List<Room> allGeneratedBounds;

    private boolean forceCourtyards = false;
    private boolean forceBedrooms = false;

    private GameObject escapeDoor;
    private GameObject winTrigger;

    private int wallTextureID;
    private int orbTextureID;
    private int woodTextureID;
    private int sheetsTextureID;

    /** Returns the seed used in the most recent {@link #generateWorld} call. */
    public long getLastSeed() {
        return lastSeed;
    }

    /**
     * Creates and returns a World object using a specific seed (for run save/load).
     */
    public World generateWorld(int wallTextureID, int orbTextureID, int woodTextureID,
                               int sheetsTextureID, long seed) {
        this.lastSeed  = seed;
        this.random    = new Random(seed);
        this.staticObjects        = new ArrayList<>();
        this.allRooms             = new ArrayList<>();
        this.allGeneratedBounds   = new ArrayList<>();
        this.wallTextureID   = wallTextureID;
        this.orbTextureID    = orbTextureID;
        this.woodTextureID   = woodTextureID;
        this.sheetsTextureID = sheetsTextureID;
        this.forceCourtyards = BuildManager.getBoolean("feature.allcourtyards.enabled");
        this.forceBedrooms   = BuildManager.getBoolean("feature.allbedrooms.enabled");
        return buildWorld();
    }

    /**
     * Creates and returns a World object containing all static GameObjects and room data.
     */
    public World generateWorld(int wallTextureID, int orbTextureID, int woodTextureID, int sheetsTextureID) {
        this.lastSeed        = new Random().nextLong();
        this.random          = new Random(lastSeed);
        this.staticObjects   = new ArrayList<>();
        this.allRooms        = new ArrayList<>();
        this.allGeneratedBounds = new ArrayList<>();
        this.wallTextureID   = wallTextureID;
        this.orbTextureID    = orbTextureID;
        this.woodTextureID   = woodTextureID;
        this.sheetsTextureID = sheetsTextureID;
        this.forceCourtyards = BuildManager.getBoolean("feature.allcourtyards.enabled");
        this.forceBedrooms   = BuildManager.getBoolean("feature.allbedrooms.enabled");
        return buildWorld();
    }

    /**
     * Core world-building logic shared by both generateWorld overloads.
     * Assumes random, staticObjects, allRooms, textures, and flags are already set.
     */
    private World buildWorld() {
        this.escapeDoor = null;
        this.winTrigger = null;

        int totalRoomsToGenerate = random.nextInt(2) + 50; // 50 or 51
        List<Room> roomsToProcess = new ArrayList<>();

        // 3. Create the first (start) room
        float startSizeX = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);
        float startSizeZ = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);

        RoomType startRoomType;
        if (this.forceBedrooms) {
            startRoomType = RoomType.BEDROOM;
        } else if (this.forceCourtyards) {
            startRoomType = RoomType.COURTYARD;
        } else {
            startRoomType = RoomType.STANDARD;
        }

        Room startRoom = new Room(
                -startSizeX / 2.0f, -startSizeZ / 2.0f,
                startSizeX / 2.0f,  startSizeZ / 2.0f,
                startRoomType
        );
        allRooms.add(startRoom);
        allGeneratedBounds.add(startRoom);
        roomsToProcess.add(startRoom);
        log("generateWorld", "Created Start Room (" + startRoomType + ") at (0, 0) with size (" + startSizeX + ", " + startSizeZ + ")");

        log("generateWorld", "Starting generation. Goal: " + totalRoomsToGenerate + " rooms + 1 Escape Tunnel.");
        boolean escapeTunnelBuilt = false;

        // 4. Main generation loop
        while (allRooms.size() < totalRoomsToGenerate && !roomsToProcess.isEmpty() && !escapeTunnelBuilt) {

            Room currentRoom = roomsToProcess.get(random.nextInt(roomsToProcess.size()));
            List<Direction> availableWalls = currentRoom.getAvailableWalls();

            if (availableWalls.isEmpty()) {
                roomsToProcess.remove(currentRoom);
                continue;
            }

            boolean success = false;
            for (int attempt = 0; attempt < 4 && !availableWalls.isEmpty(); attempt++) {
                Direction buildDirection = availableWalls.get(random.nextInt(availableWalls.size()));

                boolean isEscapeTunnel = allRooms.size() == totalRoomsToGenerate - 1;
                boolean isCourtyard = false;
                boolean isBedroom = false;

                if (!isEscapeTunnel) {
                    if (this.forceBedrooms) {
                        isBedroom = true;
                    } else if (this.forceCourtyards) {
                        isCourtyard = true;
                    } else {
                        boolean defaultCourtyard = random.nextInt(5) == 0; // 1 in 5
                        if (defaultCourtyard) {
                            isCourtyard = true;
                        } else {
                            isBedroom = random.nextInt(10) == 0; // 1 in 10
                        }
                    }
                }

                Room newRoom = buildTunnelAndNextRoom(currentRoom, buildDirection, isCourtyard, isBedroom, isEscapeTunnel);

                if (newRoom != null || (newRoom == null && isEscapeTunnel)) {
                    if (newRoom != null) {
                        allRooms.add(newRoom);
                        roomsToProcess.add(newRoom);
                        log("generateWorld", "Successfully added new " + newRoom.getType() + " room. Total rooms: " + allRooms.size());
                    } else if (isEscapeTunnel) {
                        roomsToProcess.remove(currentRoom);
                        escapeTunnelBuilt = true;
                    }
                    success = true;
                    break;
                } else {
                    availableWalls.remove(buildDirection);
                }
            }

            if (!success) {
                roomsToProcess.remove(currentRoom);
            }
        }

        if (escapeTunnelBuilt) {
            log("generateWorld", "Generation complete. Escape tunnel was built.");
        } else {
            log("generateWorld", "Generation FAILED. WARNING: Escape tunnel was NOT built.");
        }

        // 5. Build all room geometry
        for (Room room : allRooms) {
            buildRoomFloorAndRoof(room);
            buildRoomWalls(room);

            if (room.getType() == RoomType.COURTYARD) {
                generateTreesForRoom(room);
            } else if (room.getType() == RoomType.BEDROOM) {
                generateBedForRoom(room); // 💥 This is the modified method 💥
            }
        }

        log("generateWorld", "Returning new World with " + staticObjects.size() + " static objects.");
        return new World(staticObjects, allRooms, escapeDoor, winTrigger, wallTextureID, orbTextureID, woodTextureID, sheetsTextureID);
    }

    /**
     * --- Helper to build just the floor and roof for a room ---
     */
    private void buildRoomFloorAndRoof(Room room) {
        int floorTexture;
        if (room.getType() == RoomType.COURTYARD) {
            floorTexture = orbTextureID;
        } else if (room.getType() == RoomType.BEDROOM) {
            floorTexture = woodTextureID;
        } else {
            floorTexture = wallTextureID;
        }
        String roomType = room.getType().toString();

        GameObject floor = new GameObject(ShapeType.PLANE, room.getCenterX(), 0.0f, room.getCenterZ(), room.getWidth(), 0.0f, room.getDepth(), floorTexture);
        staticObjects.add(floor);
        log("buildRoomFloorAndRoof", "Added " + roomType + " Floor at " + pos(floor));

        if (room.getType() == RoomType.STANDARD || room.getType() == RoomType.BEDROOM) {
            GameObject roof = new GameObject(ShapeType.CUBE, room.getCenterX(), WALL_HEIGHT, room.getCenterZ(), room.getWidth(), WALL_THICKNESS, room.getDepth(), wallTextureID);
            staticObjects.add(roof);
            log("buildRoomFloorAndRoof", "Added " + roomType + " Roof at " + pos(roof));
        }
    }

    // --- 💥 NEW: Helper method to build a standard table 💥 ---
    private void buildTable(float tableX, float tableZ) {
        // 💥 MODIFIED: Now uses WOOD TEXTURE instead of brown color 💥
        // Table Top
        staticObjects.add(new GameObject(ShapeType.CUBE, tableX, TABLE_TOP_Y - (TABLE_TOP_H / 2.0f), tableZ,
                TABLE_TOP_W, TABLE_TOP_H, TABLE_TOP_D, woodTextureID));
        // Leg 1 (Front-Left)
        staticObjects.add(new GameObject(ShapeType.CUBE, tableX - TABLE_LEG_OFFSET_X, 0.0f, tableZ - TABLE_LEG_OFFSET_Z,
                TABLE_LEG_SIZE, TABLE_LEG_HEIGHT, TABLE_LEG_SIZE, woodTextureID));
        // Leg 2 (Front-Right)
        staticObjects.add(new GameObject(ShapeType.CUBE, tableX + TABLE_LEG_OFFSET_X, 0.0f, tableZ - TABLE_LEG_OFFSET_Z,
                TABLE_LEG_SIZE, TABLE_LEG_HEIGHT, TABLE_LEG_SIZE, woodTextureID));
        // Leg 3 (Back-Left)
        staticObjects.add(new GameObject(ShapeType.CUBE, tableX - TABLE_LEG_OFFSET_X, 0.0f, tableZ + TABLE_LEG_OFFSET_Z,
                TABLE_LEG_SIZE, TABLE_LEG_HEIGHT, TABLE_LEG_SIZE, woodTextureID));
        // Leg 4 (Back-Right)
        staticObjects.add(new GameObject(ShapeType.CUBE, tableX + TABLE_LEG_OFFSET_X, 0.0f, tableZ + TABLE_LEG_OFFSET_Z,
                TABLE_LEG_SIZE, TABLE_LEG_HEIGHT, TABLE_LEG_SIZE, woodTextureID));
    }
    // --- 💥 END NEW 💥 ---

    private void buildBed(float bedX, float bedZ) {
        // --- 1. Create Legs (Cubes, Collidable, Wood Texture) ---
        // Rotated 90 degrees: Bed body is BED_LENGTH (X) x BED_WIDTH (Z). Use BED_LENGTH for X-offset and BED_WIDTH for Z-offset.
        staticObjects.add(new GameObject(ShapeType.CUBE, bedX - (BED_LENGTH/2 - BED_LEG_SIZE/2), 0.0f, bedZ - (BED_WIDTH/2 - BED_LEG_SIZE/2),
                BED_LEG_SIZE, BED_LEG_HEIGHT, BED_LEG_SIZE, woodTextureID));
        staticObjects.add(new GameObject(ShapeType.CUBE, bedX + (BED_LENGTH/2 - BED_LEG_SIZE/2), 0.0f, bedZ - (BED_WIDTH/2 - BED_LEG_SIZE/2),
                BED_LEG_SIZE, BED_LEG_HEIGHT, BED_LEG_SIZE, woodTextureID));
        staticObjects.add(new GameObject(ShapeType.CUBE, bedX - (BED_LENGTH/2 - BED_LEG_SIZE/2), 0.0f, bedZ + (BED_WIDTH/2 - BED_LEG_SIZE/2),
                BED_LEG_SIZE, BED_LEG_HEIGHT, BED_LEG_SIZE, woodTextureID));
        staticObjects.add(new GameObject(ShapeType.CUBE, bedX + (BED_LENGTH/2 - BED_LEG_SIZE/2), 0.0f, bedZ + (BED_WIDTH/2 - BED_LEG_SIZE/2),
                BED_LEG_SIZE, BED_LEG_HEIGHT, BED_LEG_SIZE, woodTextureID));

        // --- 2. Create Mattress (Cube, Collidable, Sheets Texture) ---
        // Scale is now [BED_LENGTH] wide (X) and [BED_WIDTH] deep (Z)
        staticObjects.add(new GameObject(ShapeType.CUBE, bedX, BED_LEG_HEIGHT, bedZ,
                BED_LENGTH, BED_HEIGHT, BED_WIDTH, sheetsTextureID));

        // --- 3. Create Headboard (Cube, Collidable, Wood Texture) ---
        // Place at the +Z end of the bed (towards the room wall)
        float headboardZ = bedZ + (BED_WIDTH / 2.0f) + (HEADBOARD_THICKNESS / 2.0f);
        // Headboard scale is now BED_LENGTH wide (X)
        staticObjects.add(new GameObject(ShapeType.CUBE, bedX, BED_LEG_HEIGHT, headboardZ,
                BED_LENGTH, HEADBOARD_HEIGHT, HEADBOARD_THICKNESS, woodTextureID));
    }
    // --- 💥 END NEW 💥 ---

    /**
     * --- 💥 REWRITTEN: Generates two beds in corners with tables ---
     */
    private void generateBedForRoom(Room room) {
        log("generateBedForRoom", "Generating 2 beds and 2 tables for Bedroom at " + room.getCenterX() + ", " + room.getCenterZ());

        // --- Placement Strategy: Place against the "back" (North, +Z) wall, but rotated 90 degrees ---

        // --- Bed 1 (North-West Corner) ---
        // X: (wall + padding + half_new_width [BED_LENGTH/2])
        // Z: (wall - padding - half_new_depth [BED_WIDTH/2])
        float bed1X = room.minX + FURNITURE_WALL_PADDING + (BED_LENGTH / 2.0f);
        float bed1Z = room.maxZ - FURNITURE_WALL_PADDING - (BED_WIDTH / 2.0f);
        buildBed(bed1X, bed1Z);

        // --- Table 1 (Right of Bed 1) ---
        // X: (bed_edge + padding + half_table_width). Bed edge is now BED_LENGTH/2
        // Z: (wall - padding - half_table_depth)
        float table1X = (bed1X + BED_LENGTH / 2.0f) + FURNITURE_WALL_PADDING + (TABLE_TOP_W / 2.0f);
        float table1Z = room.maxZ - FURNITURE_WALL_PADDING - (TABLE_TOP_D / 2.0f);
        buildTable(table1X, table1Z);

        // --- Bed 2 (North-East Corner) ---
        // X: (wall - padding - half_new_width [BED_LENGTH/2])
        // Z: (wall - padding - half_new_depth [BED_WIDTH/2])
        float bed2X = room.maxX - FURNITURE_WALL_PADDING - (BED_LENGTH / 2.0f);
        float bed2Z = room.maxZ - FURNITURE_WALL_PADDING - (BED_WIDTH / 2.0f);
        buildBed(bed2X, bed2Z);

        // --- Table 2 (Left of Bed 2) ---
        // X: (bed_edge - padding - half_table_width). Bed edge is now BED_LENGTH/2
        // Z: (wall - padding - half_table_depth)
        float table2X = (bed2X - BED_LENGTH / 2.0f) - FURNITURE_WALL_PADDING - (TABLE_TOP_W / 2.0f);
        float table2Z = room.maxZ - FURNITURE_WALL_PADDING - (TABLE_TOP_D / 2.0f);
        buildTable(table2X, table2Z);

        // --- 💥 IMPORTANT: Save table locations for KeyManager 💥 ---
        // We add the Y-coordinate for the *top* of the table
        float keySpawnY = TABLE_TOP_Y + 0.05f; // Same as KeyManager logic
        room.keySpawnLocations.add(new float[]{table1X, keySpawnY, table1Z});
        room.keySpawnLocations.add(new float[]{table2X, keySpawnY, table2Z});

        log("generateBedForRoom", "Added furniture and 2 key spawn points.");
    }


    /**
     * NEW: Generates trees randomly within a Courtyard room.
     */
    private void generateTreesForRoom(Room room) {
        log("generateTreesForRoom", "Generating " + TREES_PER_COURTYARD + " trees for Courtyard at " + room.getCenterX() + ", " + room.getCenterZ());
        for (int i = 0; i < TREES_PER_COURTYARD; i++) {

            float treeMinX = room.minX + TREE_INSET;
            float treeMaxX = room.maxX - TREE_INSET;
            float treeMinZ = room.minZ + TREE_INSET;
            float treeMaxZ = room.maxZ - TREE_INSET;

            if (treeMinX >= treeMaxX || treeMinZ >= treeMaxZ) {
                log("generateTreesForRoom", "Room is too small, skipping remaining trees.");
                break;
            }

            float treeX = randRange(treeMinX, treeMaxX);
            // --- 💥💥💥 THE FIX 💥💥💥 ---
            // Was using treeMaxX by mistake, now uses treeMaxZ
            float treeZ = randRange(treeMinZ, treeMaxZ);
            // --- 💥💥💥 END FIX 💥💥💥 ---

            boolean safeToPlace = true;
            for (GameObject existingObj : staticObjects) {
                if (existingObj.getShape() == ShapeType.CUBE && existingObj.getScaleY() == TRUNK_HEIGHT) {
                    float dx = existingObj.getPosX() - treeX;
                    float dz = existingObj.getPosZ() - treeZ;
                    if (dx * dx + dz * dz < (LEAF_RADIUS * 2) * (LEAF_RADIUS * 2)) {
                        safeToPlace = false;
                        break;
                    }
                }
            }

            if (safeToPlace) {
                GameObject trunk = new GameObject(
                        ShapeType.CUBE,
                        treeX, 0.0f, treeZ,
                        TRUNK_WIDTH, TRUNK_HEIGHT, TRUNK_WIDTH,
                        0.5f, 0.3f, 0.0f,
                        true, true
                );

                float leafY = TRUNK_HEIGHT;
                GameObject leaves = new GameObject(
                        ShapeType.SPHERE,
                        treeX, leafY, treeZ,
                        LEAF_RADIUS, LEAF_RADIUS, LEAF_RADIUS,
                        0.0f, 0.7f, 0.0f,
                        false, true
                );

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

        int wallTex = room.getType() == RoomType.COURTYARD ? 0 : wallTextureID;
        float wallHeight = room.getType() == RoomType.COURTYARD ? 100.0f : WALL_HEIGHT;
        boolean collidable = true;
        boolean renderable = room.getType() != RoomType.COURTYARD;

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
     */
    private Room buildTunnelAndNextRoom(Room fromRoom, Direction direction, boolean isCourtyard, boolean isBedroom, boolean isEscapeTunnel) {
        fromRoom.markWallUsed(direction);

        float tunnelLength = randRange(MIN_TUNNEL_LENGTH, MAX_TUNNEL_LENGTH);
        float newRoomWidth = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);
        float newRoomDepth = randRange(MIN_ROOM_SIZE, MAX_ROOM_SIZE);

        float newRoomMinX=0, newRoomMinZ=0, newRoomMaxX=0, newRoomMaxZ=0;
        float tunnelMinX=0, tunnelMinZ=0, tunnelMaxX=0, tunnelMaxZ=0;

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
                newRoomMinX = tunnelMinX;
                newRoomMaxX = tunnelMinX + newRoomWidth;
                break;
        }

        Room tunnelBounds = new Room(tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);
        Room newRoomBounds = null;
        if (!isEscapeTunnel) {
            RoomType type;
            if (isCourtyard) {
                type = RoomType.COURTYARD;
            } else if (isBedroom) {
                type = RoomType.BEDROOM;
            } else {
                type = RoomType.STANDARD;
            }
            newRoomBounds = new Room(newRoomMinX, newRoomMinZ, newRoomMaxX, newRoomMaxZ, type);
        }

        for (Room existingBounds : allGeneratedBounds) {
            if (existingBounds != fromRoom) {
                if (tunnelBounds.overlaps(existingBounds, 1.0f)) {
                    log("buildTunnelAndNextRoom", "TUNNEL COLLISION DETECTED. Failed to build " + direction);
                    fromRoom.unmarkWallUsed(direction);
                    return null;
                }
            }
            if (newRoomBounds != null && newRoomBounds.overlaps(existingBounds, 1.0f)) {
                log("buildTunnelAndNextRoom", "ROOM COLLISION DETECTED with " + (existingBounds == fromRoom ? "ITS PARENT" : "another bound") + ". Failed to build " + direction);
                fromRoom.unmarkWallUsed(direction);
                return null;
            }
        }

        if (isEscapeTunnel) {
            buildDeadEndEscapeTunnel(fromRoom, direction, tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);
            allGeneratedBounds.add(tunnelBounds);
            return null;
        }

        newRoomBounds.markWallUsed(direction.getOpposite());
        buildTunnelObjects(tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);
        allGeneratedBounds.add(tunnelBounds);
        allGeneratedBounds.add(newRoomBounds);
        return newRoomBounds;
    }

    /**
     * --- Builds the Dead-End Escape Hallway with Door and Win Trigger ---
     */
    private void buildDeadEndEscapeTunnel(Room fromRoom, Direction direction, float tunnelMinX, float tunnelMinZ, float tunnelMaxX, float tunnelMaxZ) {
        log("buildDeadEndEscapeTunnel", "--- GENERATING ESCAPE TUNNEL ---");
        log("buildDeadEndEscapeTunnel", "Building exit from Room at: (" + fromRoom.getCenterX() + ", " + fromRoom.getCenterZ() + ") facing " + direction);

        float centerX = (tunnelMinX + tunnelMaxX) / 2.0f;
        float centerZ = (tunnelMinZ + tunnelMaxZ) / 2.0f;
        float width = tunnelMaxX - tunnelMinX;
        float depth = tunnelMaxZ - tunnelMinZ;

        buildTunnelObjects(tunnelMinX, tunnelMinZ, tunnelMaxX, tunnelMaxZ);

        float doorX = 0, doorZ = 0, doorW = 0, doorH = TUNNEL_HEIGHT, doorD = 0;
        float endWallX = 0, endWallZ = 0, endWallW = 0, endWallD = 0;
        float triggerX = 0, triggerZ = 0, triggerW = 0, triggerD = 0;

        float triggerHalfSize = WIN_TRIGGER_SIZE / 2.0f;
        float wallHalfSize = WALL_THICKNESS / 2.0f;

        switch (direction) {
            case NORTH:
                doorX = fromRoom.getCenterX(); doorZ = fromRoom.maxZ + (WALL_THICKNESS / 2.0f);
                doorW = TUNNEL_WIDTH; doorD = WALL_THICKNESS;
                endWallX = centerX; endWallZ = tunnelMaxZ - wallHalfSize;
                endWallW = width; endWallD = WALL_THICKNESS;
                triggerX = centerX; triggerZ = endWallZ - wallHalfSize - triggerHalfSize;
                triggerW = width; triggerD = WIN_TRIGGER_SIZE;
                break;
            case SOUTH:
                doorX = fromRoom.getCenterX(); doorZ = fromRoom.minZ - (WALL_THICKNESS / 2.0f);
                doorW = TUNNEL_WIDTH; doorD = WALL_THICKNESS;
                endWallX = centerX; endWallZ = tunnelMinZ + wallHalfSize;
                endWallW = width; endWallD = WALL_THICKNESS;
                triggerX = centerX; triggerZ = endWallZ + wallHalfSize + triggerHalfSize;
                triggerW = width; triggerD = WIN_TRIGGER_SIZE;
                break;
            case EAST:
                doorX = fromRoom.maxX + (WALL_THICKNESS / 2.0f); doorZ = fromRoom.getCenterZ();
                doorW = WALL_THICKNESS; doorD = TUNNEL_WIDTH;
                endWallX = tunnelMaxX - wallHalfSize; endWallZ = centerZ;
                endWallW = WALL_THICKNESS; endWallD = depth;
                triggerX = endWallX - wallHalfSize - triggerHalfSize; triggerZ = centerZ;
                triggerW = WIN_TRIGGER_SIZE; triggerD = depth;
                break;
            case WEST:
                doorX = fromRoom.minX - (WALL_THICKNESS / 2.0f); doorZ = fromRoom.getCenterZ();
                doorW = WALL_THICKNESS; doorD = TUNNEL_WIDTH;
                endWallX = tunnelMinX + wallHalfSize; endWallZ = centerZ;
                endWallW = WALL_THICKNESS; endWallD = depth;
                triggerX = endWallX + wallHalfSize + triggerHalfSize; triggerZ = centerZ;
                triggerW = WIN_TRIGGER_SIZE; triggerD = depth;
                break;
        }

        escapeDoor = new GameObject(ShapeType.CUBE, doorX, 0.0f, doorZ, doorW, doorH, doorD, 0.5f, 0.3f, 0.0f, true, true);
        staticObjects.add(escapeDoor);
        log("buildDeadEndEscapeTunnel", "Added ESCAPE DOOR at " + pos(escapeDoor));

        GameObject endWall = new GameObject(
                ShapeType.CUBE,
                endWallX, 0.0f, endWallZ,
                endWallW, TUNNEL_HEIGHT, endWallD,
                wallTextureID
        );
        staticObjects.add(endWall);
        log("buildDeadEndEscapeTunnel", "Added End Wall at " + pos(endWall));

        winTrigger = new GameObject(ShapeType.CUBE, triggerX, 0.0f, triggerZ,
                triggerW, TUNNEL_HEIGHT, triggerD,
                1.0f, 1.0f, 1.0f,
                false, true);
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

        GameObject floor = new GameObject(ShapeType.CUBE, centerX, 0.0f, centerZ, width, 0.1f, depth, wallTextureID);
        staticObjects.add(floor);
        log("buildTunnelObjects", "Added Tunnel Floor at " + pos(floor));
        GameObject roof = new GameObject(ShapeType.CUBE, centerX, TUNNEL_HEIGHT, centerZ, width, 0.1f, depth, wallTextureID);
        staticObjects.add(roof);
        log("buildTunnelObjects", "Added Tunnel Roof at " + pos(roof));

        if (width > depth) {
            GameObject wallN = new GameObject(ShapeType.CUBE, centerX, 0.0f, maxZ - (WALL_THICKNESS / 2.0f), width, TUNNEL_HEIGHT, WALL_THICKNESS, wallTextureID);
            staticObjects.add(wallN);
            log("buildTunnelObjects", "Added Tunnel N Wall at " + pos(wallN));
            GameObject wallS = new GameObject(ShapeType.CUBE, centerX, 0.0f, minZ + (WALL_THICKNESS / 2.0f), width, TUNNEL_HEIGHT, WALL_THICKNESS, wallTextureID);
            staticObjects.add(wallS);
            log("buildTunnelObjects", "Added Tunnel S Wall at " + pos(wallS));
        } else {
            GameObject wallE = new GameObject(ShapeType.CUBE, maxX - (WALL_THICKNESS / 2.0f), 0.0f, centerZ, WALL_THICKNESS, TUNNEL_HEIGHT, depth, wallTextureID);
            staticObjects.add(wallE);
            log("buildTunnelObjects", "Added Tunnel E Wall at " + pos(wallE));
            GameObject wallW = new GameObject(ShapeType.CUBE, minX + (WALL_THICKNESS / 2.0f), 0.0f, centerZ, WALL_THICKNESS, TUNNEL_HEIGHT, depth, wallTextureID);
            staticObjects.add(wallW);
            log("buildTunnelObjects", "Added Tunnel W Wall at " + pos(wallW));
        }
    }

    private void buildWall(float minX, float maxX, float minZ, float maxZ, boolean isVertical, int textureId, boolean collidable, boolean renderable, float height) {
        float centerX = (minX + maxX) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;
        float width = isVertical ? WALL_THICKNESS : (maxX - minX);
        float depth = isVertical ? (maxZ - minZ) : WALL_THICKNESS;

        GameObject wall = new GameObject(ShapeType.CUBE, centerX, 0.0f, centerZ, width, height, depth, textureId, collidable, renderable);
        staticObjects.add(wall);
        log("buildWall", "Added Solid Wall at " + pos(wall));
    }

    private void buildWallWithHole(float minX, float maxX, float minZ, float maxZ, float holeCenter, int textureId, boolean collidable, boolean renderable) {
        float halfGap = TUNNEL_WIDTH / 2.0f;
        float lintelY = TUNNEL_HEIGHT;
        float lintelHeight = WALL_HEIGHT - TUNNEL_HEIGHT;
        float finalWallHeight = collidable ? WALL_HEIGHT : 100.0f;

        boolean isVertical = (minX == maxX);

        if (isVertical) {
            float segmentLength1 = (holeCenter - halfGap) - minZ;
            float segmentLength2 = maxZ - (holeCenter + halfGap);
            float wallX = minX;

            GameObject seg1 = new GameObject(ShapeType.CUBE, wallX, 0.0f, minZ + segmentLength1 / 2.0f, WALL_THICKNESS, finalWallHeight, segmentLength1, textureId, collidable, renderable);
            staticObjects.add(seg1);
            GameObject seg2 = new GameObject(ShapeType.CUBE, wallX, 0.0f, maxZ - segmentLength2 / 2.0f, WALL_THICKNESS, finalWallHeight, segmentLength2, textureId, collidable, renderable);
            staticObjects.add(seg2);
            GameObject lintel = new GameObject(ShapeType.CUBE, wallX, lintelY, holeCenter, WALL_THICKNESS, lintelHeight, TUNNEL_WIDTH, textureId, collidable, renderable);
            staticObjects.add(lintel);
            log("buildWallWithHole", "Added Vertical Holed Wall (3 parts) at X=" + wallX);

        } else {
            float segmentLength1 = (holeCenter - halfGap) - minX;
            float segmentLength2 = maxX - (holeCenter + halfGap);
            float wallZ = minZ;

            GameObject seg1 = new GameObject(ShapeType.CUBE, minX + segmentLength1 / 2.0f, 0.0f, wallZ, segmentLength1, finalWallHeight, WALL_THICKNESS, textureId, collidable, renderable);
            staticObjects.add(seg1);
            GameObject seg2 = new GameObject(ShapeType.CUBE, maxX - segmentLength2 / 2.0f, 0.0f, wallZ, segmentLength2, finalWallHeight, WALL_THICKNESS, textureId, collidable, renderable);
            staticObjects.add(seg2);
            GameObject lintel = new GameObject(ShapeType.CUBE, holeCenter, lintelY, wallZ, TUNNEL_WIDTH, lintelHeight, WALL_THICKNESS, textureId, collidable, renderable);
            staticObjects.add(lintel);
            log("buildWallWithHole", "Added Horizontal Holed Wall (3 parts) at Z=" + wallZ);
        }
    }

    private float randRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private String pos(GameObject obj) {
        return String.format("(%.2f, %.2f, %.2f)", obj.getPosX(), obj.getPosY(), obj.getPosZ());
    }

    private void log(String method, String message) {
        System.out.println("[WorldLoader - " + method + "] " + message);
    }
}
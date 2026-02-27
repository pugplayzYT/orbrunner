package ohio.pugnetgames.chad.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Room: overlap detection, geometry, wall management,
 * and available wall tracking.
 */
class RoomTest {

    // -------------------------------------------------------------------------
    // Construction & type
    // -------------------------------------------------------------------------

    @Test
    void defaultConstructorCreatesStandardRoom() {
        Room room = new Room(0, 0, 10, 10);
        assertEquals(Room.RoomType.STANDARD, room.getType());
    }

    @Test
    void courtyardTypeIsStored() {
        Room room = new Room(0, 0, 10, 10, Room.RoomType.COURTYARD);
        assertEquals(Room.RoomType.COURTYARD, room.getType());
    }

    @Test
    void bedroomTypeIsStored() {
        Room room = new Room(0, 0, 10, 10, Room.RoomType.BEDROOM);
        assertEquals(Room.RoomType.BEDROOM, room.getType());
    }

    // -------------------------------------------------------------------------
    // Center & dimensions
    // -------------------------------------------------------------------------

    @Test
    void centerXIsCorrect() {
        Room room = new Room(0, 0, 10, 10);
        assertEquals(5.0f, room.getCenterX(), 0.001f);
    }

    @Test
    void centerZIsCorrect() {
        Room room = new Room(0, 0, 10, 10);
        assertEquals(5.0f, room.getCenterZ(), 0.001f);
    }

    @Test
    void centerWithNonSymmetricBounds() {
        Room room = new Room(2.0f, 4.0f, 8.0f, 14.0f);
        assertEquals(5.0f, room.getCenterX(), 0.001f);
        assertEquals(9.0f, room.getCenterZ(), 0.001f);
    }

    @Test
    void widthIsCorrect() {
        Room room = new Room(0, 0, 10, 8);
        assertEquals(10.0f, room.getWidth(), 0.001f);
    }

    @Test
    void depthIsCorrect() {
        Room room = new Room(0, 0, 10, 8);
        assertEquals(8.0f, room.getDepth(), 0.001f);
    }

    // -------------------------------------------------------------------------
    // Overlap detection
    // -------------------------------------------------------------------------

    @Test
    void overlappingRoomsReturnTrue() {
        Room a = new Room(0, 0, 10, 10);
        Room b = new Room(5, 5, 15, 15);
        assertTrue(a.overlaps(b, 0));
    }

    @Test
    void nonOverlappingRoomsReturnFalse() {
        Room a = new Room(0, 0, 5, 5);
        Room b = new Room(10, 10, 15, 15);
        assertFalse(a.overlaps(b, 0));
    }

    @Test
    void touchingEdgesWithZeroPaddingDoNotOverlap() {
        Room a = new Room(0, 0, 5, 5);
        Room b = new Room(5, 0, 10, 5);
        // maxX of a == minX of b → condition: maxX + 0 > minX is 5 > 5 which is false
        assertFalse(a.overlaps(b, 0));
    }

    @Test
    void paddingCausesNearbyRoomsToOverlap() {
        Room a = new Room(0, 0, 5, 5);
        Room b = new Room(7, 0, 12, 5);  // 2 units gap
        // Without padding: no overlap
        assertFalse(a.overlaps(b, 0));
        // With padding of 3: overlap
        assertTrue(a.overlaps(b, 3));
    }

    @Test
    void overlapIsSymmetric() {
        Room a = new Room(0, 0, 10, 10);
        Room b = new Room(5, 5, 15, 15);
        assertEquals(a.overlaps(b, 0), b.overlaps(a, 0));
    }

    // -------------------------------------------------------------------------
    // Wall marking
    // -------------------------------------------------------------------------

    @Test
    void allWallsUnusedByDefault() {
        Room room = new Room(0, 0, 10, 10);
        assertFalse(room.northWallUsed);
        assertFalse(room.southWallUsed);
        assertFalse(room.eastWallUsed);
        assertFalse(room.westWallUsed);
    }

    @Test
    void markNorthWall() {
        Room room = new Room(0, 0, 10, 10);
        room.markWallUsed(Direction.NORTH);
        assertTrue(room.northWallUsed);
        assertFalse(room.southWallUsed);
        assertFalse(room.eastWallUsed);
        assertFalse(room.westWallUsed);
    }

    @Test
    void markSouthWall() {
        Room room = new Room(0, 0, 10, 10);
        room.markWallUsed(Direction.SOUTH);
        assertTrue(room.southWallUsed);
    }

    @Test
    void markEastWall() {
        Room room = new Room(0, 0, 10, 10);
        room.markWallUsed(Direction.EAST);
        assertTrue(room.eastWallUsed);
    }

    @Test
    void markWestWall() {
        Room room = new Room(0, 0, 10, 10);
        room.markWallUsed(Direction.WEST);
        assertTrue(room.westWallUsed);
    }

    @Test
    void unmarkWallRestoresAvailability() {
        Room room = new Room(0, 0, 10, 10);
        room.markWallUsed(Direction.NORTH);
        room.unmarkWallUsed(Direction.NORTH);
        assertFalse(room.northWallUsed);
    }

    // -------------------------------------------------------------------------
    // Available walls
    // -------------------------------------------------------------------------

    @Test
    void allFourWallsAvailableInitially() {
        Room room = new Room(0, 0, 10, 10);
        List<Direction> walls = room.getAvailableWalls();
        assertEquals(4, walls.size());
        assertTrue(walls.contains(Direction.NORTH));
        assertTrue(walls.contains(Direction.SOUTH));
        assertTrue(walls.contains(Direction.EAST));
        assertTrue(walls.contains(Direction.WEST));
    }

    @Test
    void markingWallReducesAvailableCount() {
        Room room = new Room(0, 0, 10, 10);
        room.markWallUsed(Direction.NORTH);
        List<Direction> walls = room.getAvailableWalls();
        assertEquals(3, walls.size());
        assertFalse(walls.contains(Direction.NORTH));
    }

    @Test
    void markingAllWallsLeavesNoneAvailable() {
        Room room = new Room(0, 0, 10, 10);
        for (Direction dir : Direction.values()) {
            room.markWallUsed(dir);
        }
        assertTrue(room.getAvailableWalls().isEmpty());
    }

    @Test
    void keySpawnLocationsEmptyByDefault() {
        Room room = new Room(0, 0, 10, 10);
        assertTrue(room.keySpawnLocations.isEmpty());
    }

    @Test
    void keySpawnLocationsCanBeAdded() {
        Room room = new Room(0, 0, 10, 10);
        room.keySpawnLocations.add(new float[]{5.0f, 1.0f, 5.0f});
        assertEquals(1, room.keySpawnLocations.size());
    }
}

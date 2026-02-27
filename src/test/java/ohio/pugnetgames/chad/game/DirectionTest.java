package ohio.pugnetgames.chad.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Direction enum and its getOpposite() logic.
 */
class DirectionTest {

    @Test
    void northOppositeIsSouth() {
        assertEquals(Direction.SOUTH, Direction.NORTH.getOpposite());
    }

    @Test
    void southOppositeIsNorth() {
        assertEquals(Direction.NORTH, Direction.SOUTH.getOpposite());
    }

    @Test
    void eastOppositeIsWest() {
        assertEquals(Direction.WEST, Direction.EAST.getOpposite());
    }

    @Test
    void westOppositeIsEast() {
        assertEquals(Direction.EAST, Direction.WEST.getOpposite());
    }

    @Test
    void oppositeOfOppositeIsOriginal() {
        for (Direction dir : Direction.values()) {
            assertEquals(dir, dir.getOpposite().getOpposite(),
                "Calling getOpposite() twice should return the original direction for: " + dir);
        }
    }

    @Test
    void noDirectionReturnsNullOpposite() {
        for (Direction dir : Direction.values()) {
            assertNotNull(dir.getOpposite(),
                "getOpposite() should never return null for: " + dir);
        }
    }

    @Test
    void allFourDirectionsExist() {
        assertEquals(4, Direction.values().length);
    }
}

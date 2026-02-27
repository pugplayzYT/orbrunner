package ohio.pugnetgames.chad.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RunState — ensures all saved fields survive construction.
 */
class RunStateTest {

    @Test
    void constructorStoresWorldSeed() {
        RunState state = new RunState(123456789L, 0, 0, 0, 0, 0, new boolean[0]);
        assertEquals(123456789L, state.worldSeed);
    }

    @Test
    void constructorStoresPlayerPosition() {
        RunState state = new RunState(0L, 3.14f, 1.5f, -2.71f, 0, 0, new boolean[0]);
        assertEquals(3.14f,  state.playerX, 0.001f);
        assertEquals(1.5f,   state.playerY, 0.001f);
        assertEquals(-2.71f, state.playerZ, 0.001f);
    }

    @Test
    void constructorStoresCameraOrientation() {
        RunState state = new RunState(0L, 0, 0, 0, 90.0f, -15.0f, new boolean[0]);
        assertEquals(90.0f,  state.yaw,   0.001f);
        assertEquals(-15.0f, state.pitch, 0.001f);
    }

    @Test
    void constructorStoresKeysCollectedArray() {
        boolean[] keys = {true, false, true};
        RunState state = new RunState(0L, 0, 0, 0, 0, 0, keys);
        assertArrayEquals(keys, state.keysCollected);
    }

    @Test
    void emptyKeysArrayIsAllowed() {
        RunState state = new RunState(0L, 0, 0, 0, 0, 0, new boolean[0]);
        assertNotNull(state.keysCollected);
        assertEquals(0, state.keysCollected.length);
    }

    @Test
    void allKeysCollectedState() {
        boolean[] keys = {true, true, true, true, true};
        RunState state = new RunState(42L, 0, 0, 0, 0, 0, keys);
        for (boolean k : state.keysCollected) {
            assertTrue(k);
        }
    }

    @Test
    void noKeysCollectedState() {
        boolean[] keys = {false, false, false};
        RunState state = new RunState(0L, 0, 0, 0, 0, 0, keys);
        for (boolean k : state.keysCollected) {
            assertFalse(k);
        }
    }

    @Test
    void negativeSeedIsStored() {
        RunState state = new RunState(-999L, 0, 0, 0, 0, 0, new boolean[0]);
        assertEquals(-999L, state.worldSeed);
    }
}

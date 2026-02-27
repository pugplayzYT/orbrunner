package ohio.pugnetgames.chad.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Key data class.
 */
class KeyTest {

    @Test
    void constructorStoresPosition() {
        Key key = new Key(1.0f, 2.5f, -3.0f);
        assertEquals(1.0f, key.x, 0.001f);
        assertEquals(2.5f, key.y, 0.001f);
        assertEquals(-3.0f, key.z, 0.001f);
    }

    @Test
    void newKeyIsNotCollected() {
        Key key = new Key(0, 0, 0);
        assertFalse(key.collected, "A newly created key should not be collected");
    }

    @Test
    void newKeyHasZeroRotation() {
        Key key = new Key(0, 0, 0);
        assertEquals(0.0f, key.rotation, 0.001f);
    }

    @Test
    void keyCanBeMarkedCollected() {
        Key key = new Key(5.0f, 1.0f, 5.0f);
        key.collected = true;
        assertTrue(key.collected);
    }

    @Test
    void keyRotationCanBeUpdated() {
        Key key = new Key(0, 0, 0);
        key.rotation = 90.0f;
        assertEquals(90.0f, key.rotation, 0.001f);
    }

    @Test
    void keyAtOrigin() {
        Key key = new Key(0.0f, 0.0f, 0.0f);
        assertEquals(0.0f, key.x, 0.001f);
        assertEquals(0.0f, key.y, 0.001f);
        assertEquals(0.0f, key.z, 0.001f);
    }

    @Test
    void keyWithNegativeCoordinates() {
        Key key = new Key(-10.0f, -5.0f, -20.0f);
        assertEquals(-10.0f, key.x, 0.001f);
        assertEquals(-5.0f, key.y, 0.001f);
        assertEquals(-20.0f, key.z, 0.001f);
    }
}

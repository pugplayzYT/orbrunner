package ohio.pugnetgames.chad.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Player state management — position getters/setters,
 * teleportation, and camera orientation storage.
 *
 * Note: tests that exercise update() are omitted here because that
 * method orchestrates physics against the live World graph and
 * applies OpenGL camera transforms, both of which require a running
 * GLFW/OpenGL context.  All logic below runs purely on the JVM.
 */
class PlayerTest {

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void constructorSetsStartPosition() {
        Player player = new Player(1.0f, 1.5f, -3.0f);
        assertEquals(1.0f,  player.getPosX(), 0.001f);
        assertEquals(1.5f,  player.getPosY(), 0.001f);
        assertEquals(-3.0f, player.getPosZ(), 0.001f);
    }

    @Test
    void constructorSetsZeroYawAndPitch() {
        Player player = new Player(0, 0, 0);
        assertEquals(0.0f, player.getYaw(),   0.001f);
        assertEquals(0.0f, player.getPitch(), 0.001f);
    }

    @Test
    void eyeHeightConstantIsPositive() {
        Player player = new Player(0, 0, 0);
        assertTrue(player.PLAYER_EYE_HEIGHT > 0,
            "Eye height must be a positive value");
    }

    // -------------------------------------------------------------------------
    // Individual setters
    // -------------------------------------------------------------------------

    @Test
    void setPosXUpdatesX() {
        Player player = new Player(0, 0, 0);
        player.setPosX(7.5f);
        assertEquals(7.5f, player.getPosX(), 0.001f);
    }

    @Test
    void setPosYUpdatesY() {
        Player player = new Player(0, 0, 0);
        player.setPosY(3.0f);
        assertEquals(3.0f, player.getPosY(), 0.001f);
    }

    @Test
    void setPosZUpdatesZ() {
        Player player = new Player(0, 0, 0);
        player.setPosZ(-9.0f);
        assertEquals(-9.0f, player.getPosZ(), 0.001f);
    }

    @Test
    void setYawUpdatesYaw() {
        Player player = new Player(0, 0, 0);
        player.setYaw(180.0f);
        assertEquals(180.0f, player.getYaw(), 0.001f);
    }

    @Test
    void setPitchUpdatesPitch() {
        Player player = new Player(0, 0, 0);
        player.setPitch(-45.0f);
        assertEquals(-45.0f, player.getPitch(), 0.001f);
    }

    // -------------------------------------------------------------------------
    // teleportTo
    // -------------------------------------------------------------------------

    @Test
    void teleportToSetsAllCoordinates() {
        Player player = new Player(0, 0, 0);
        player.teleportTo(10.0f, 2.0f, -5.0f);
        assertEquals(10.0f,  player.getPosX(), 0.001f);
        assertEquals(2.0f,   player.getPosY(), 0.001f);
        assertEquals(-5.0f,  player.getPosZ(), 0.001f);
    }

    @Test
    void teleportToOverwritesExistingPosition() {
        Player player = new Player(5.0f, 1.5f, 5.0f);
        player.teleportTo(0.0f, 0.0f, 0.0f);
        assertEquals(0.0f, player.getPosX(), 0.001f);
        assertEquals(0.0f, player.getPosY(), 0.001f);
        assertEquals(0.0f, player.getPosZ(), 0.001f);
    }

    @Test
    void teleportToNegativeCoordinates() {
        Player player = new Player(0, 0, 0);
        player.teleportTo(-100.0f, 1.5f, -200.0f);
        assertEquals(-100.0f, player.getPosX(), 0.001f);
        assertEquals(1.5f,    player.getPosY(), 0.001f);
        assertEquals(-200.0f, player.getPosZ(), 0.001f);
    }

    // -------------------------------------------------------------------------
    // Run-restore round-trip
    // -------------------------------------------------------------------------

    @Test
    void restorePositionFromRunState() {
        Player player = new Player(0, 0, 0);
        float savedX = 3.14f, savedY = 1.5f, savedZ = -2.71f;
        float savedYaw = 90.0f, savedPitch = -15.0f;

        player.setPosX(savedX);
        player.setPosY(savedY);
        player.setPosZ(savedZ);
        player.setYaw(savedYaw);
        player.setPitch(savedPitch);

        assertEquals(savedX,     player.getPosX(),   0.001f);
        assertEquals(savedY,     player.getPosY(),   0.001f);
        assertEquals(savedZ,     player.getPosZ(),   0.001f);
        assertEquals(savedYaw,   player.getYaw(),    0.001f);
        assertEquals(savedPitch, player.getPitch(),  0.001f);
    }
}

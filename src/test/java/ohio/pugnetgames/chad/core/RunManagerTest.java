package ohio.pugnetgames.chad.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RunManager covering:
 *  - sanitizeName() (pure string logic, no I/O)
 *  - saveRunState() / loadRunState() round-trip using a temp folder
 */
class RunManagerTest {

    private final RunManager manager = new RunManager();

    // -------------------------------------------------------------------------
    // sanitizeName — pure string logic
    // -------------------------------------------------------------------------

    @Test
    void sanitizeConvertsToLowerCase() {
        assertEquals("hello-world", RunManager.sanitizeName("Hello World"));
    }

    @Test
    void sanitizeReplacesSpacesWithDashes() {
        assertEquals("my-cool-run", RunManager.sanitizeName("my cool run"));
    }

    @Test
    void sanitizeRemovesSpecialCharacters() {
        assertEquals("my-run", RunManager.sanitizeName("My Run!!!"));
    }

    @Test
    void sanitizeCollapsesMultipleSpacesIntoOneDash() {
        assertEquals("a-b", RunManager.sanitizeName("a   b"));
    }

    @Test
    void sanitizeTrimsLeadingAndTrailingSpaces() {
        assertEquals("spaces", RunManager.sanitizeName("  spaces  "));
    }

    @Test
    void sanitizeCollapsesMultipleDashes() {
        assertEquals("a-b", RunManager.sanitizeName("a--b"));
    }

    @Test
    void sanitizeStripsLeadingDash() {
        assertEquals("run", RunManager.sanitizeName("-run"));
    }

    @Test
    void sanitizeStripsTrailingDash() {
        assertEquals("run", RunManager.sanitizeName("run-"));
    }

    @Test
    void sanitizePreservesNumbers() {
        assertEquals("run-5000", RunManager.sanitizeName("Run 5000"));
    }

    @Test
    void sanitizeEmptyStringReturnsEmpty() {
        assertEquals("", RunManager.sanitizeName(""));
    }

    @Test
    void sanitizeOnlySpecialCharsReturnsEmpty() {
        assertEquals("", RunManager.sanitizeName("!!!"));
    }

    @Test
    void sanitizeComplexName() {
        assertEquals("extra-cool-run-5000",
                     RunManager.sanitizeName("Extra Cool Run 5000"));
    }

    // -------------------------------------------------------------------------
    // saveRunState / loadRunState — full round-trip with a temp dir
    // -------------------------------------------------------------------------

    /** Builds a RunData pointing at the given temp directory. */
    private RunData makeRunData(Path folder) {
        return new RunData("test-id", "Test Run", Difficulty.EASY,
                           System.currentTimeMillis(), 0L,
                           RunData.STATUS_IN_PROGRESS, 0L, folder);
    }

    @Test
    void saveAndLoadPreservesWorldSeed(@TempDir Path tmpDir) {
        RunData run   = makeRunData(tmpDir);
        RunState saved = new RunState(987654321L, 0, 0, 0, 0, 0, new boolean[]{false});
        manager.saveRunState(run, saved);

        RunState loaded = manager.loadRunState(run);
        assertNotNull(loaded);
        assertEquals(987654321L, loaded.worldSeed);
    }

    @Test
    void saveAndLoadPreservesPlayerPosition(@TempDir Path tmpDir) {
        RunData run    = makeRunData(tmpDir);
        RunState saved = new RunState(1L, 3.14f, 1.5f, -2.71f, 0, 0, new boolean[]{false});
        manager.saveRunState(run, saved);

        RunState loaded = manager.loadRunState(run);
        assertNotNull(loaded);
        assertEquals(3.14f,  loaded.playerX, 0.001f);
        assertEquals(1.5f,   loaded.playerY, 0.001f);
        assertEquals(-2.71f, loaded.playerZ, 0.001f);
    }

    @Test
    void saveAndLoadPreservesCameraOrientation(@TempDir Path tmpDir) {
        RunData run    = makeRunData(tmpDir);
        RunState saved = new RunState(1L, 0, 0, 0, 90.0f, -15.0f, new boolean[]{false});
        manager.saveRunState(run, saved);

        RunState loaded = manager.loadRunState(run);
        assertNotNull(loaded);
        assertEquals(90.0f,  loaded.yaw,   0.001f);
        assertEquals(-15.0f, loaded.pitch, 0.001f);
    }

    @Test
    void saveAndLoadPreservesKeysCollected(@TempDir Path tmpDir) {
        RunData run    = makeRunData(tmpDir);
        boolean[] keys = {true, false, true, false};
        RunState saved = new RunState(1L, 0, 0, 0, 0, 0, keys);
        manager.saveRunState(run, saved);

        RunState loaded = manager.loadRunState(run);
        assertNotNull(loaded);
        assertArrayEquals(keys, loaded.keysCollected);
    }

    @Test
    void loadRunStateReturnsNullWhenFileAbsent(@TempDir Path tmpDir) {
        RunData run = makeRunData(tmpDir);
        // Don't save anything — state.dat does not exist
        assertNull(manager.loadRunState(run));
    }

    @Test
    void saveOverwritesPreviousState(@TempDir Path tmpDir) {
        RunData run = makeRunData(tmpDir);

        RunState first  = new RunState(1L, 1.0f, 1.5f, 1.0f, 0, 0, new boolean[]{false});
        RunState second = new RunState(2L, 9.0f, 1.5f, 9.0f, 45.0f, 0, new boolean[]{true});

        manager.saveRunState(run, first);
        manager.saveRunState(run, second);

        RunState loaded = manager.loadRunState(run);
        assertNotNull(loaded);
        assertEquals(2L,    loaded.worldSeed);
        assertEquals(9.0f,  loaded.playerX, 0.001f);
        assertEquals(45.0f, loaded.yaw,     0.001f);
        assertTrue(loaded.keysCollected[0]);
    }

    @Test
    void saveAndLoadAllKeysTrue(@TempDir Path tmpDir) {
        RunData run    = makeRunData(tmpDir);
        boolean[] keys = {true, true, true, true, true};
        manager.saveRunState(run, new RunState(0L, 0, 0, 0, 0, 0, keys));

        RunState loaded = manager.loadRunState(run);
        assertNotNull(loaded);
        for (boolean k : loaded.keysCollected) {
            assertTrue(k);
        }
    }

    @Test
    void saveAndLoadAllKeysFalse(@TempDir Path tmpDir) {
        RunData run    = makeRunData(tmpDir);
        boolean[] keys = {false, false, false};
        manager.saveRunState(run, new RunState(0L, 0, 0, 0, 0, 0, keys));

        RunState loaded = manager.loadRunState(run);
        assertNotNull(loaded);
        for (boolean k : loaded.keysCollected) {
            assertFalse(k);
        }
    }
}

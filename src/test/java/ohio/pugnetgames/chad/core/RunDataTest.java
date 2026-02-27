package ohio.pugnetgames.chad.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RunData: status checks and elapsed-time formatting.
 */
class RunDataTest {

    private RunData makeRun(String status, long elapsedMs) {
        return new RunData("test-run", "Test Run", Difficulty.EASY,
                           1_000_000L, 0L, status, elapsedMs,
                           Paths.get(System.getProperty("user.home")));
    }

    // -------------------------------------------------------------------------
    // isCompleted()
    // -------------------------------------------------------------------------

    @Test
    void isCompletedReturnsTrueForCompletedStatus() {
        RunData run = makeRun(RunData.STATUS_COMPLETED, 0);
        assertTrue(run.isCompleted());
    }

    @Test
    void isCompletedReturnsFalseForInProgressStatus() {
        RunData run = makeRun(RunData.STATUS_IN_PROGRESS, 0);
        assertFalse(run.isCompleted());
    }

    // -------------------------------------------------------------------------
    // formatElapsed()
    // -------------------------------------------------------------------------

    @Test
    void formatElapsedZeroMilliseconds() {
        RunData run = makeRun(RunData.STATUS_IN_PROGRESS, 0);
        assertEquals("0s", run.formatElapsed());
    }

    @Test
    void formatElapsedLessThanOneMinute() {
        long ms = 45 * 1000L; // 45 seconds
        RunData run = makeRun(RunData.STATUS_IN_PROGRESS, ms);
        assertEquals("45s", run.formatElapsed());
    }

    @Test
    void formatElapsedExactlyOneMinute() {
        long ms = 60 * 1000L;
        RunData run = makeRun(RunData.STATUS_IN_PROGRESS, ms);
        assertEquals("1m 0s", run.formatElapsed());
    }

    @Test
    void formatElapsedMinutesAndSeconds() {
        long ms = (14 * 60 + 32) * 1000L; // 14m 32s
        RunData run = makeRun(RunData.STATUS_IN_PROGRESS, ms);
        assertEquals("14m 32s", run.formatElapsed());
    }

    @Test
    void formatElapsedOneHour() {
        long ms = 60 * 60 * 1000L; // 60 minutes
        RunData run = makeRun(RunData.STATUS_COMPLETED, ms);
        assertEquals("60m 0s", run.formatElapsed());
    }

    @Test
    void formatElapsedSubSecondRoundsDown() {
        long ms = 500L; // 0.5 seconds → 0 full seconds
        RunData run = makeRun(RunData.STATUS_IN_PROGRESS, ms);
        assertEquals("0s", run.formatElapsed());
    }

    @Test
    void formatElapsedJustUnderOneMinute() {
        long ms = 59 * 1000L; // 59 seconds
        RunData run = makeRun(RunData.STATUS_IN_PROGRESS, ms);
        assertEquals("59s", run.formatElapsed());
    }

    // -------------------------------------------------------------------------
    // Field storage
    // -------------------------------------------------------------------------

    @Test
    void constructorStoresAllFields() {
        RunData run = new RunData("my-run", "My Run", Difficulty.HARD,
                                  1000L, 2000L, RunData.STATUS_COMPLETED,
                                  5000L, Paths.get("/tmp/test"));
        assertEquals("my-run",                   run.id);
        assertEquals("My Run",                    run.displayName);
        assertEquals(Difficulty.HARD,             run.difficulty);
        assertEquals(1000L,                       run.createdAt);
        assertEquals(2000L,                       run.completedAt);
        assertEquals(RunData.STATUS_COMPLETED,    run.status);
        assertEquals(5000L,                       run.elapsedMs);
    }

    @Test
    void statusConstantsHaveExpectedValues() {
        assertEquals("IN_PROGRESS", RunData.STATUS_IN_PROGRESS);
        assertEquals("COMPLETED",   RunData.STATUS_COMPLETED);
    }
}

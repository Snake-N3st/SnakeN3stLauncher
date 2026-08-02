package mc.snakenest.launcher.bugreport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashLoopWatchdogTest {

    @Test
    void doesNotTripOnASingleException() {
        CrashLoopWatchdog watchdog = new CrashLoopWatchdog();

        assertFalse(watchdog.recordAndCheckTripped());
    }

    @Test
    void doesNotTripOnAFewExceptionsBelowTheThreshold() {
        CrashLoopWatchdog watchdog = new CrashLoopWatchdog();

        for (int i = 0; i < 20; i++) {
            assertFalse(watchdog.recordAndCheckTripped());
        }
    }

    @Test
    void tripsOnceEnoughExceptionsArriveInAShortBurst() {
        CrashLoopWatchdog watchdog = new CrashLoopWatchdog();

        boolean tripped = false;
        for (int i = 0; i < 100; i++) {
            tripped = watchdog.recordAndCheckTripped();
        }

        assertTrue(tripped);
    }

    @Test
    void staysTrippedOnEveryCallAfterTheThresholdIsReached() {
        CrashLoopWatchdog watchdog = new CrashLoopWatchdog();
        for (int i = 0; i < 100; i++) {
            watchdog.recordAndCheckTripped();
        }

        assertTrue(watchdog.recordAndCheckTripped());
    }

    @Test
    void oldExceptionsOutsideTheWindowDoNotCountTowardTheThreshold() throws InterruptedException {
        CrashLoopWatchdog watchdog = new CrashLoopWatchdog();

        // A handful of exceptions, spaced out well past the detection window, must never look
        // like a burst - this is what tells "the launcher had a rough evening" apart from "the
        // launcher is stuck re-throwing right now".
        for (int i = 0; i < 8; i++) {
            assertFalse(watchdog.recordAndCheckTripped());
            Thread.sleep(700);
        }
    }
}
